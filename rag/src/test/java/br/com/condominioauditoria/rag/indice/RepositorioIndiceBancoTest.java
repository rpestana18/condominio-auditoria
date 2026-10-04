package br.com.condominioauditoria.rag.indice;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.rag.indice.RepositorioIndice.Achado;
import br.com.condominioauditoria.rag.indice.RepositorioIndice.FiltrosBusca;
import br.com.condominioauditoria.rag.mensagens.IndexarArquivo;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * SQL do índice contra um PostgreSQL de verdade com pgvector (imagem pgvector/pgvector:pg17). Só roda com a variável
 * RAG_TESTE_BANCO_URL apontando para um banco descartável, por exemplo:
 *
 * <pre>
 * docker run -d --name rag-pg-teste -e POSTGRES_USER=condominio -e POSTGRES_PASSWORD=condominio \
 *     -e POSTGRES_DB=condominio -p 55432:5432 pgvector/pgvector:pg17
 * RAG_TESTE_BANCO_URL=jdbc:postgresql://localhost:55432/condominio ./gradlew :rag:test --no-parallel
 * </pre>
 *
 * Cada teste usa um condomínio novo, então pode rodar de novo no mesmo banco.
 */
@EnabledIfEnvironmentVariable(named = "RAG_TESTE_BANCO_URL", matches = ".+")
class RepositorioIndiceBancoTest {

    private static RepositorioIndice repositorio;
    private static JdbcClient jdbc;

    @BeforeAll
    static void migrar() {
        String url = System.getenv("RAG_TESTE_BANCO_URL") + "?currentSchema=rag,public";
        var dados = new DriverManagerDataSource(url, "condominio", "condominio");
        Flyway.configure().dataSource(dados).schemas("rag").defaultSchema("rag").load().migrate();
        jdbc = JdbcClient.create(dados);
        repositorio = new RepositorioIndice(jdbc, new JdbcTemplate(dados),
                new TransactionTemplate(new DataSourceTransactionManager(dados)));
    }

    @Test
    void indexaBuscaPorPalavraSemAcentoEPorVetorComFiltros() {
        UUID condominio = UUID.randomUUID();
        IndexarArquivo ata = pedido(condominio, "ATA", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        IndexarArquivo contrato = pedido(condominio, "CONTRATO", null, null);
        gravar(ata, List.of(
                trecho(1, new Localizacao.Pagina(1), "A assembleia aprovou a manutenção do elevador."),
                trecho(2, new Localizacao.Pagina(2), "Multa de 2% por atraso no pagamento da cota.")), 0);
        gravar(contrato, List.of(
                trecho(1, new Localizacao.Planilha("Valores", 2, 31), "Item | Valor\nManutencao mensal | 1500.00"),
                trecho(2, new Localizacao.Paragrafos(3, 5, ""), "Reajuste anual pelo IGP-M.")), 2);

        var todos = new FiltrosBusca(condominio, null, null, null, null);

        // Sem acento acha com acento e vice-versa; plural regular pelo radical do português ("pagamentos").
        // O radicalizador do PostgreSQL não une -ção e -ções (limitação conhecida, com ou sem unaccent).
        List<Achado> manutencao = repositorio.buscarPorPalavra(todos, "manutencao", 10);
        assertThat(manutencao).hasSize(2);
        assertThat(repositorio.buscarPorPalavra(todos, "Manutenção", 10)).hasSize(2);
        assertThat(repositorio.buscarPorPalavra(todos, "pagamentos", 10)).hasSize(1);
        // Exclusão com "-" e frase entre aspas (websearch_to_tsquery)
        assertThat(repositorio.buscarPorPalavra(todos, "manutenção -elevador", 10)).hasSize(1);
        assertThat(repositorio.buscarPorPalavra(todos, "\"multa de 2%\"", 10)).hasSize(1);

        // Filtros no WHERE: categoria, período (documento sem competência sai), arquivos, outro condomínio
        assertThat(repositorio.buscarPorPalavra(new FiltrosBusca(condominio, List.of("CONTRATO"), null, null, null),
                "manutencao", 10)).hasSize(1);
        assertThat(repositorio.buscarPorPalavra(new FiltrosBusca(condominio, null, LocalDate.of(2026, 9, 15), null,
                null), "manutencao", 10)).hasSize(1);
        assertThat(repositorio.buscarPorPalavra(new FiltrosBusca(condominio, null, null, LocalDate.of(2026, 8, 31),
                null), "manutencao", 10)).isEmpty();
        assertThat(repositorio.buscarPorPalavra(new FiltrosBusca(condominio, null, null, null,
                List.of(contrato.arquivoId())), "manutencao", 10)).hasSize(1);
        assertThat(repositorio.buscarPorPalavra(new FiltrosBusca(UUID.randomUUID(), null, null, null, null),
                "manutencao", 10)).isEmpty();

        // Vetor: o mais parecido primeiro (vetores de teste = eixo de cada trecho)
        List<UUID> porVetor = repositorio.buscarPorVetor(todos, eixo(2), "bge-m3", 10);
        assertThat(porVetor).hasSize(4);
        List<TrechoEncontrado> carregados = repositorio.carregar(porVetor);
        assertThat(carregados.getFirst().localizacao()).isEqualTo(new Localizacao.Planilha("Valores", 2, 31));
        assertThat(carregados.getFirst().sha256()).isEqualTo(contrato.sha256());
        assertThat(repositorio.buscarPorVetor(new FiltrosBusca(condominio, List.of("ATA"), null, null, null),
                eixo(2), "bge-m3", 10)).hasSize(2);

        // Exclusão lógica: some das duas buscas, mas continua no banco
        assertThat(repositorio.retirar(ata.arquivoId(), UUID.randomUUID())).isTrue();
        assertThat(repositorio.buscarPorPalavra(todos, "elevador", 10)).isEmpty();
        assertThat(repositorio.buscarPorVetor(todos, eixo(0), "bge-m3", 10)).hasSize(2);
        assertThat(repositorio.buscar(ata.arquivoId()).orElseThrow().retirado()).isTrue();
    }

    @Test
    void reindexarSubstituiOsTrechosESemTextoNaoDeixaNada() {
        UUID condominio = UUID.randomUUID();
        IndexarArquivo ata = pedido(condominio, "ATA", null, null);
        gravar(ata, List.of(trecho(1, new Localizacao.Pagina(1), "primeira versão"),
                trecho(2, new Localizacao.Pagina(2), "segunda página")), 0);
        var todos = new FiltrosBusca(condominio, null, null, null, null);
        assertThat(repositorio.buscarPorPalavra(todos, "página", 10)).hasSize(1);

        gravar(ata, List.of(trecho(1, new Localizacao.Pagina(1), "texto novo")), 0);
        assertThat(repositorio.buscarPorPalavra(todos, "página", 10)).isEmpty();
        assertThat(repositorio.buscarPorPalavra(todos, "novo", 10)).hasSize(1);
        assertThat(repositorio.buscar(ata.arquivoId()).orElseThrow().trechos()).isEqualTo(1);

        repositorio.substituir(ata, new DocumentoCortado(3, List.of(), "PDF sem texto extraível"), null, null);
        var doc = repositorio.buscar(ata.arquivoId()).orElseThrow();
        assertThat(doc.estado()).isEqualTo("sem_texto");
        assertThat(doc.motivo()).isEqualTo("PDF sem texto extraível");
        assertThat(doc.modeloEmbeddings()).isNull();
        assertThat(jdbc.sql("select count(*) from trecho where arquivo_id = :a").param("a", ata.arquivoId())
                .query(Integer.class).single()).isZero();
    }

    @Test
    void erroGuardaMotivoSemMexerNosTrechosAntigos() {
        UUID condominio = UUID.randomUUID();
        IndexarArquivo ata = pedido(condominio, "ATA", null, null);
        gravar(ata, List.of(trecho(1, new Localizacao.Pagina(1), "conteúdo antigo")), 0);

        repositorio.marcarIndexando(ata);
        repositorio.marcarErro(ata.arquivoId(), ata.indexacaoId(), "Ollama fora do ar");

        var doc = repositorio.buscar(ata.arquivoId()).orElseThrow();
        assertThat(doc.estado()).isEqualTo("erro");
        assertThat(doc.motivo()).isEqualTo("Ollama fora do ar");
        assertThat(repositorio.buscarPorPalavra(new FiltrosBusca(condominio, null, null, null, null), "antigo", 10))
                .hasSize(1);
    }

    @Test
    void barraValeComoEspacoEExclusaoEFraseValemNoLadoVetorial() {
        UUID condominio = UUID.randomUUID();
        IndexarArquivo folha = pedido(condominio, "FOLHA", null, null);
        gravar(folha, List.of(
                trecho(1, new Localizacao.Pagina(1), "Salário base do zelador"),
                trecho(2, new Localizacao.Pagina(2), "Salário e Vale Transporte"),
                trecho(3, new Localizacao.Pagina(3), "Despesas Transporte/Combustível do salário"),
                trecho(4, new Localizacao.Pagina(4), "Folha de pagamento: encargos")), 0);
        var todos = new FiltrosBusca(condominio, null, null, null, null);

        // Palavra: "Transporte/Combustível" agora casa com transporte e com combustivel
        assertThat(repositorio.buscarPorPalavra(todos, "combustivel", 10)).hasSize(1);
        assertThat(repositorio.buscarPorPalavra(todos, "transporte", 10)).hasSize(2);
        assertThat(repositorio.buscarPorPalavra(todos, "salário -transporte", 10)).hasSize(1);

        // Vetor: sem restrição vêm os 4; com exclusão, os que têm transporte saem; com frase, só quem a contém
        assertThat(repositorio.buscarPorVetor(todos, eixo(1), "bge-m3", 10)).hasSize(4);
        List<UUID> semTransporte = repositorio.buscarPorVetor(todos, eixo(1), "bge-m3", 10,
                RestricoesBusca.extrair("salário -transporte"));
        assertThat(repositorio.carregar(semTransporte)).extracting(TrechoEncontrado::localizacao)
                .containsExactlyInAnyOrder(new Localizacao.Pagina(1), new Localizacao.Pagina(4));
        List<UUID> frase = repositorio.buscarPorVetor(todos, eixo(1), "bge-m3", 10,
                RestricoesBusca.extrair("\"folha de pagamento\" encargos"));
        assertThat(repositorio.carregar(frase)).extracting(TrechoEncontrado::localizacao)
                .containsExactly(new Localizacao.Pagina(4));
        // Restrição só com palavra vazia não zera a busca
        assertThat(repositorio.buscarPorVetor(todos, eixo(1), "bge-m3", 10, "-de")).hasSize(4);
    }

    @Test
    void indexadoSemVetorEntraNaBuscaPorPalavraComAviso() {
        UUID condominio = UUID.randomUUID();
        IndexarArquivo ata = pedido(condominio, "ATA", null, null);
        repositorio.marcarIndexando(ata);
        repositorio.substituir(ata, new DocumentoCortado(1, List.of(trecho(1, new Localizacao.Pagina(1),
                "Multa por atraso")), null), null, null, "Indexado só para a busca por palavra: Ollama fora");

        var doc = repositorio.buscar(ata.arquivoId()).orElseThrow();
        assertThat(doc.estado()).isEqualTo("indexado");
        assertThat(doc.modeloEmbeddings()).isNull();
        assertThat(doc.motivo()).contains("Ollama fora");
        var todos = new FiltrosBusca(condominio, null, null, null, null);
        assertThat(repositorio.buscarPorPalavra(todos, "multa", 10)).hasSize(1);
        assertThat(repositorio.buscarPorVetor(todos, eixo(0), "bge-m3", 10)).isEmpty();
    }

    private static void gravar(IndexarArquivo pedido, List<TrechoCortado> trechos, int primeiroEixo) {
        repositorio.marcarIndexando(pedido);
        List<float[]> vetores = new java.util.ArrayList<>();
        for (int i = 0; i < trechos.size(); i++) {
            vetores.add(eixo(primeiroEixo + i));
        }
        repositorio.substituir(pedido, new DocumentoCortado(2, trechos, null), vetores, "bge-m3");
    }

    private static float[] eixo(int i) {
        float[] v = new float[GeradorEmbeddings.DIMENSOES];
        v[i] = 1f;
        v[GeradorEmbeddings.DIMENSOES - 1] = 0.1f; // nenhum vetor nulo (cosseno indefinido)
        return v;
    }

    private static TrechoCortado trecho(int ordem, Localizacao local, String texto) {
        return new TrechoCortado(ordem, local, texto);
    }

    private static IndexarArquivo pedido(UUID condominio, String categoria, LocalDate inicio, LocalDate fim) {
        UUID arquivo = UUID.randomUUID();
        return new IndexarArquivo(1, IndexarArquivo.Operacao.INDEXAR, UUID.randomUUID(), arquivo, condominio,
                categoria, categoria.toLowerCase() + ".pdf", "c/" + arquivo + ".pdf",
                UUID.randomUUID().toString().replace("-", "").repeat(2), inicio, fim, 1, null, null);
    }
}
