package br.com.condominioauditoria.rag.indice;

import br.com.condominioauditoria.rag.messaging.IndexFileMessage;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * SQL do índice no schema rag (ADR 0003, Decisão 3): documento_indexado, trecho e trecho_vetor. Sem JPA e sem
 * PgVectorStore; o vetor vai como texto e o banco converte (cast(... as public.vector)).
 *
 * Os dados do documento (sha256, nome, categoria) só mudam junto com os trechos, na mesma transação. Assim os trechos
 * no índice sempre correspondem ao sha256 gravado, mesmo que uma reindexação falhe no meio.
 */
@Repository
public class RepositorioIndice {

    /** Só nomes simples de modelo entram literalmente no SQL (para o índice HNSW parcial por modelo ser usado). */
    private static final Pattern NOME_MODELO = Pattern.compile("[A-Za-z0-9._:-]{1,100}");

    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transacao;

    public RepositorioIndice(JdbcClient jdbc, JdbcTemplate jdbcTemplate, TransactionTemplate transacao) {
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
        this.transacao = transacao;
    }

    /** Estado atual do índice de um arquivo. */
    public record DocumentoIndexado(UUID arquivoId, String sha256, String estado, String motivo, Integer paginas,
            Integer trechos, String modeloEmbeddings, String versaoIndexador, boolean retirado) {
    }

    public Optional<DocumentoIndexado> buscar(UUID arquivoId) {
        return jdbc.sql("""
                select arquivo_id, sha256, estado, motivo, paginas, trechos, modelo_embeddings, versao_indexador, retirado
                  from documento_indexado where arquivo_id = :arquivo""")
                .param("arquivo", arquivoId)
                .query((rs, i) -> new DocumentoIndexado(rs.getObject("arquivo_id", UUID.class), rs.getString("sha256"),
                        rs.getString("estado"), rs.getString("motivo"), (Integer) rs.getObject("paginas"),
                        (Integer) rs.getObject("trechos"), rs.getString("modelo_embeddings"),
                        rs.getString("versao_indexador"), rs.getBoolean("retirado")))
                .optional();
    }

    /** Começo do trabalho. Linha nova nasce com os dados do pedido; linha existente só muda estado (ver classe). */
    public void marcarIndexando(IndexFileMessage p) {
        jdbc.sql("""
                insert into documento_indexado (arquivo_id, condominio_id, categoria, nome_original, caminho, sha256,
                    competencia_inicio, competencia_fim, versao_arquivo, estado, indexacao_id)
                values (:arquivo, :condominio, :categoria, :nome, :caminho, :sha256, :inicio, :fim, :versaoArquivo,
                    'indexando', :indexacao)
                on conflict (arquivo_id) do update
                   set estado = 'indexando', motivo = null, indexacao_id = excluded.indexacao_id, atualizado_em = now()""")
                .params(dadosDoPedido(p))
                .update();
    }

    public void marcarErro(UUID arquivoId, UUID indexacaoId, String motivo) {
        jdbc.sql("""
                update documento_indexado set estado = 'erro', motivo = :motivo, indexacao_id = :indexacao,
                       atualizado_em = now()
                 where arquivo_id = :arquivo""")
                .param("arquivo", arquivoId).param("indexacao", indexacaoId).param("motivo", motivo)
                .update();
    }

    /** Pedido repetido de conteúdo já indexado: atualiza só os dados do documento e volta a mostrá-lo na busca. */
    public void confirmarSemReindexar(IndexFileMessage p) {
        jdbc.sql("""
                update documento_indexado
                   set condominio_id = :condominio, categoria = :categoria, nome_original = :nome, caminho = :caminho,
                       competencia_inicio = :inicio, competencia_fim = :fim, versao_arquivo = :versaoArquivo,
                       retirado = false, indexacao_id = :indexacao, atualizado_em = now()
                 where arquivo_id = :arquivo""")
                .params(dadosDoPedido(p))
                .update();
    }

    /** Exclusão lógica: some da busca, nada é apagado. Devolve false se o arquivo nunca foi indexado. */
    public boolean retirar(UUID arquivoId, UUID indexacaoId) {
        return jdbc.sql("""
                update documento_indexado set retirado = true, indexacao_id = :indexacao, atualizado_em = now()
                 where arquivo_id = :arquivo""")
                .param("arquivo", arquivoId).param("indexacao", indexacaoId)
                .update() > 0;
    }

    /**
     * Substitui, numa transação só, os trechos e vetores do arquivo pelos novos e grava os dados do documento.
     * Sem trechos = estado sem_texto. {@code vetores} nulo = embeddings desligados (só busca por palavra).
     */
    public void substituir(IndexFileMessage p, DocumentoCortado cortado, List<float[]> vetores, String modelo) {
        substituir(p, cortado, vetores, modelo, null);
    }

    /** {@code aviso}: guardado em motivo quando indexado (ex.: sem vetores porque o Ollama estava fora). */
    public void substituir(IndexFileMessage p, DocumentoCortado cortado, List<float[]> vetores, String modelo,
            String aviso) {
        if (vetores != null && vetores.size() != cortado.trechos().size()) {
            throw new IllegalArgumentException("Vetores (" + vetores.size() + ") e trechos ("
                    + cortado.trechos().size() + ") não batem");
        }
        transacao.executeWithoutResult(status -> {
            Map<String, Object> dados = dadosDoPedido(p);
            dados.put("estado", cortado.semTexto() ? "sem_texto" : "indexado");
            dados.put("motivo", cortado.semTexto() ? cortado.motivoSemTexto() : aviso);
            dados.put("paginas", cortado.paginas());
            dados.put("trechos", cortado.trechos().size());
            dados.put("modelo", vetores == null || cortado.semTexto() ? null : modelo);
            dados.put("versaoIndexador", CortadorTrechos.VERSAO);
            jdbc.sql("""
                    update documento_indexado
                       set condominio_id = :condominio, categoria = :categoria, nome_original = :nome,
                           caminho = :caminho, sha256 = :sha256, competencia_inicio = :inicio, competencia_fim = :fim,
                           versao_arquivo = :versaoArquivo, estado = :estado, motivo = :motivo, paginas = :paginas,
                           trechos = :trechos, modelo_embeddings = :modelo, versao_indexador = :versaoIndexador,
                           retirado = false, indexacao_id = :indexacao, atualizado_em = now()
                     where arquivo_id = :arquivo""")
                    .params(dados)
                    .update();
            jdbc.sql("delete from trecho where arquivo_id = :arquivo").param("arquivo", p.fileId()).update();

            List<TrechoCortado> trechos = cortado.trechos();
            List<UUID> ids = trechos.stream().map(t -> idDoTrecho(p.fileId(), p.sha256(), t.ordem())).toList();
            jdbcTemplate.batchUpdate("""
                    insert into trecho (id, arquivo_id, condominio_id, ordem, pagina, aba, linha_inicio, linha_fim,
                        secao, paragrafo_inicio, paragrafo_fim, texto)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", trechos, 200, (ps, t) -> {
                ps.setObject(1, ids.get(t.ordem() - 1));
                ps.setObject(2, p.fileId());
                ps.setObject(3, p.condominiumId());
                ps.setInt(4, t.ordem());
                Integer pagina = null, linhaInicio = null, linhaFim = null, paragrafoInicio = null, paragrafoFim = null;
                String aba = null, secao = null;
                switch (t.localizacao()) {
                    case Localizacao.Pagina l -> pagina = l.numero();
                    case Localizacao.Planilha l -> {
                        aba = l.aba();
                        linhaInicio = l.linhaInicio();
                        linhaFim = l.linhaFim();
                    }
                    case Localizacao.Paragrafos l -> {
                        paragrafoInicio = l.inicio();
                        paragrafoFim = l.fim();
                        secao = l.secao() == null || l.secao().isEmpty() ? null : l.secao();
                    }
                }
                ps.setObject(5, pagina);
                ps.setString(6, aba);
                ps.setObject(7, linhaInicio);
                ps.setObject(8, linhaFim);
                ps.setString(9, secao);
                ps.setObject(10, paragrafoInicio);
                ps.setObject(11, paragrafoFim);
                ps.setString(12, t.texto());
            });
            if (vetores != null && !trechos.isEmpty()) {
                List<Integer> indices = new ArrayList<>();
                for (int i = 0; i < trechos.size(); i++) {
                    indices.add(i);
                }
                jdbcTemplate.batchUpdate("""
                        insert into trecho_vetor (trecho_id, modelo, vetor) values (?, ?, cast(? as public.vector))""",
                        indices, 100, (ps, i) -> {
                            ps.setObject(1, ids.get(i));
                            ps.setString(2, modelo);
                            ps.setString(3, GeradorEmbeddings.comoTexto(vetores.get(i)));
                        });
            }
        });
    }

    /**
     * Id estável: o mesmo arquivo, conteúdo, regra de corte e posição geram sempre o mesmo id. Reindexar sem mudança
     * não quebra citações antigas.
     */
    public static UUID idDoTrecho(UUID arquivoId, String sha256, int ordem) {
        String chave = arquivoId + ":" + sha256 + ":" + CortadorTrechos.VERSAO + ":" + ordem;
        return UUID.nameUUIDFromBytes(chave.getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, Object> dadosDoPedido(IndexFileMessage p) {
        Map<String, Object> dados = new HashMap<>();
        dados.put("arquivo", p.fileId());
        dados.put("condominio", p.condominiumId());
        dados.put("categoria", p.category());
        dados.put("nome", p.originalName());
        dados.put("caminho", p.path());
        dados.put("sha256", p.sha256());
        dados.put("inicio", p.periodStart());
        dados.put("fim", p.periodEnd());
        dados.put("versaoArquivo", p.fileVersion());
        dados.put("indexacao", p.indexingId());
        return dados;
    }

    // ---------------------------------------------------------------- busca

    /** Trecho achado pela busca por palavra, com a relevância do ts_rank. */
    public record Achado(UUID id, double relevancia) {
    }

    /**
     * Busca por palavra (português sem acento). websearch_to_tsquery aceita "frase entre aspas" e exclusão com -.
     * A barra vale como espaço, como na coluna gerada (V2): "Transporte/Combustível" casa com "transporte".
     * Empate de relevância desempata pelo arquivo e pela ordem do trecho (resultado determinístico).
     */
    public List<Achado> buscarPorPalavra(FiltrosBusca filtros, String texto, int limite) {
        var where = new Filtro(filtros);
        where.parametros.put("texto", texto);
        where.parametros.put("limite", limite);
        return jdbc.sql("""
                select t.id, ts_rank(t.busca, q) as relevancia
                  from trecho t
                  join documento_indexado d on d.arquivo_id = t.arquivo_id,
                       websearch_to_tsquery('rag.portuguese_unaccent', translate(:texto, '/', ' ')) q
                 where t.busca @@ q and %s
                 order by relevancia desc, t.arquivo_id, t.ordem
                 limit :limite""".formatted(where.sql))
                .params(where.parametros)
                .query((rs, i) -> new Achado(rs.getObject("id", UUID.class), rs.getDouble("relevancia")))
                .list();
    }

    /**
     * Busca por vetor (distância de cosseno) com os mesmos filtros. A varredura iterativa do pgvector (0.8+) evita
     * que um filtro muito seletivo devolva menos resultados que o pedido.
     */
    public List<UUID> buscarPorVetor(FiltrosBusca filtros, float[] vetor, String modelo, int limite) {
        return buscarPorVetor(filtros, vetor, modelo, limite, null);
    }

    /**
     * {@code restricoes}: frases obrigatórias e termos negados da pergunta ({@link RestricoesBusca}), aplicados aos
     * candidatos vetoriais como na busca por palavra. Nulo = sem restrição.
     */
    public List<UUID> buscarPorVetor(FiltrosBusca filtros, float[] vetor, String modelo, int limite,
            String restricoes) {
        if (!NOME_MODELO.matcher(modelo).matches()) {
            throw new IllegalArgumentException("Nome de modelo inválido: " + modelo);
        }
        var where = new Filtro(filtros);
        where.parametros.put("vetor", GeradorEmbeddings.comoTexto(vetor));
        where.parametros.put("limite", limite);
        String condicaoRestricoes = "";
        if (restricoes != null && !restricoes.isBlank()) {
            // Restrição só com palavras vazias (ex.: -de) vira tsquery vazia: não restringe nada
            String consulta = "websearch_to_tsquery('rag.portuguese_unaccent', translate(:restricoes, '/', ' '))";
            condicaoRestricoes = " and (numnode(" + consulta + ") = 0 or t.busca @@ " + consulta + ")";
            where.parametros.put("restricoes", restricoes);
        }
        // Modelo literal (validado acima) para o planejador poder usar o índice HNSW parcial daquele modelo
        String sql = """
                select t.id
                  from trecho t
                  join documento_indexado d on d.arquivo_id = t.arquivo_id
                  join trecho_vetor v on v.trecho_id = t.id and v.modelo = '%s'
                 where %s%s
                 order by v.vetor <=> cast(:vetor as public.vector)
                 limit :limite""".formatted(modelo, where.sql, condicaoRestricoes);
        return transacao.execute(status -> {
            jdbc.sql("select set_config('hnsw.iterative_scan', 'strict_order', true)").query().singleValue();
            jdbc.sql("select set_config('hnsw.ef_search', '100', true)").query().singleValue();
            return jdbc.sql(sql).params(where.parametros).query((rs, i) -> rs.getObject("id", UUID.class)).list();
        });
    }

    /** Dados para mostrar e citar os trechos, na ordem pedida. Id que não existe mais fica de fora. */
    public List<TrechoEncontrado> carregar(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, TrechoEncontrado> porId = new HashMap<>();
        jdbc.sql("""
                select t.id, t.arquivo_id, d.nome_original, d.categoria, d.sha256, t.pagina, t.aba, t.linha_inicio,
                       t.linha_fim, t.secao, t.paragrafo_inicio, t.paragrafo_fim, t.texto
                  from trecho t join documento_indexado d on d.arquivo_id = t.arquivo_id
                 where t.id in (:ids)""")
                .param("ids", ids)
                .query((rs, i) -> trechoEncontrado(rs))
                .list()
                .forEach(t -> porId.put(t.trechoId(), t));
        Map<UUID, TrechoEncontrado> ordenado = new LinkedHashMap<>();
        for (UUID id : ids) {
            if (porId.containsKey(id)) {
                ordenado.put(id, porId.get(id));
            }
        }
        return List.copyOf(ordenado.values());
    }

    private static TrechoEncontrado trechoEncontrado(ResultSet rs) throws SQLException {
        Localizacao local;
        if (rs.getObject("pagina") != null) {
            local = new Localizacao.Pagina(rs.getInt("pagina"));
        } else if (rs.getString("aba") != null) {
            local = new Localizacao.Planilha(rs.getString("aba"), rs.getInt("linha_inicio"), rs.getInt("linha_fim"));
        } else {
            String secao = rs.getString("secao");
            local = new Localizacao.Paragrafos(rs.getInt("paragrafo_inicio"), rs.getInt("paragrafo_fim"),
                    secao == null ? "" : secao);
        }
        return new TrechoEncontrado(rs.getObject("id", UUID.class), rs.getObject("arquivo_id", UUID.class),
                rs.getString("nome_original"), rs.getString("categoria"), local, rs.getString("texto"), 0,
                rs.getString("sha256"));
    }

    /**
     * WHERE comum às duas buscas, aplicado antes da ordenação (ADR 0003): condomínio obrigatório, só vigentes e não
     * retirados, e os filtros opcionais de categoria, período e arquivos.
     */
    private static final class Filtro {
        private final Map<String, Object> parametros = new HashMap<>();
        private final String sql;

        public Filtro(FiltrosBusca f) {
            List<String> condicoes = new ArrayList<>();
            condicoes.add("d.condominio_id = :condominio");
            condicoes.add("d.retirado = false");
            condicoes.add("d.vigente = true");
            parametros.put("condominio", f.condominioId());
            if (!f.categorias().isEmpty()) {
                condicoes.add("d.categoria in (:categorias)");
                parametros.put("categorias", f.categorias());
            }
            if (!f.arquivoIds().isEmpty()) {
                condicoes.add("d.arquivo_id in (:arquivos)");
                parametros.put("arquivos", f.arquivoIds());
            }
            // Período: a competência do documento cruza o intervalo; documento sem competência não entra
            if (f.dataInicio() != null || f.dataFim() != null) {
                condicoes.add("coalesce(d.competencia_inicio, d.competencia_fim) is not null");
            }
            if (f.dataFim() != null) {
                condicoes.add("coalesce(d.competencia_inicio, d.competencia_fim) <= :dataFim");
                parametros.put("dataFim", Date.valueOf(f.dataFim()));
            }
            if (f.dataInicio() != null) {
                condicoes.add("coalesce(d.competencia_fim, d.competencia_inicio) >= :dataInicio");
                parametros.put("dataInicio", Date.valueOf(f.dataInicio()));
            }
            this.sql = String.join(" and ", condicoes);
        }
    }

    /** Filtros da busca; listas vazias e datas nulas = sem filtro. */
    public record FiltrosBusca(UUID condominioId, List<String> categorias, LocalDate dataInicio, LocalDate dataFim,
            List<UUID> arquivoIds) {

        public FiltrosBusca {
            if (condominioId == null) {
                throw new IllegalArgumentException("Condomínio é obrigatório na busca");
            }
            categorias = categorias == null ? List.of() : List.copyOf(categorias);
            arquivoIds = arquivoIds == null ? List.of() : List.copyOf(arquivoIds);
        }
    }
}
