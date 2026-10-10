package br.com.condominioauditoria.rag.repository;

import br.com.condominioauditoria.rag.messaging.IndexFileMessage;
import br.com.condominioauditoria.rag.search.Chunk;
import br.com.condominioauditoria.rag.search.ChunkedDocument;
import br.com.condominioauditoria.rag.search.EmbeddingGenerator;
import br.com.condominioauditoria.rag.search.FoundChunk;
import br.com.condominioauditoria.rag.search.Location;
import br.com.condominioauditoria.rag.search.TextChunker;
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
 * SQL of the index in the rag schema (ADR 0003, Decision 3): documento_indexado, trecho and trecho_vetor. No JPA and
 * no PgVectorStore; the vector goes as text and the database converts it (cast(... as public.vector)).
 *
 * The document data (sha256, name, category) only changes together with the chunks, in the same transaction. So the
 * chunks in the index always match the stored sha256, even if a reindexing fails midway.
 */
@Repository
public class IndexRepository {

    /** Only simple model names go literally into the SQL (so the partial HNSW index per model is used). */
    private static final Pattern MODEL_NAME = Pattern.compile("[A-Za-z0-9._:-]{1,100}");

    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transaction;

    public IndexRepository(JdbcClient jdbc, JdbcTemplate jdbcTemplate, TransactionTemplate transaction) {
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
        this.transaction = transaction;
    }

    /** Current index state of a file. */
    public record IndexedDocument(UUID fileId, String sha256, String state, String reason, Integer pages,
            Integer chunks, String embeddingModel, String indexerVersion, boolean withdrawn) {
    }

    public Optional<IndexedDocument> find(UUID fileId) {
        return jdbc.sql("""
                select arquivo_id, sha256, estado, motivo, paginas, trechos, modelo_embeddings, versao_indexador, retirado
                  from documento_indexado where arquivo_id = :arquivo""")
                .param("arquivo", fileId)
                .query((rs, i) -> new IndexedDocument(rs.getObject("arquivo_id", UUID.class), rs.getString("sha256"),
                        rs.getString("estado"), rs.getString("motivo"), (Integer) rs.getObject("paginas"),
                        (Integer) rs.getObject("trechos"), rs.getString("modelo_embeddings"),
                        rs.getString("versao_indexador"), rs.getBoolean("retirado")))
                .optional();
    }

    /** Start of the work. A new row is born with the request data; an existing row only changes state (see class). */
    public void markIndexing(IndexFileMessage p) {
        jdbc.sql("""
                insert into documento_indexado (arquivo_id, condominio_id, categoria, nome_original, caminho, sha256,
                    competencia_inicio, competencia_fim, versao_arquivo, estado, indexacao_id)
                values (:arquivo, :condominio, :categoria, :nome, :caminho, :sha256, :inicio, :fim, :versaoArquivo,
                    'indexando', :indexacao)
                on conflict (arquivo_id) do update
                   set estado = 'indexando', motivo = null, indexacao_id = excluded.indexacao_id, atualizado_em = now()""")
                .params(requestParams(p))
                .update();
    }

    public void markError(UUID fileId, UUID indexingId, String reason) {
        jdbc.sql("""
                update documento_indexado set estado = 'erro', motivo = :motivo, indexacao_id = :indexacao,
                       atualizado_em = now()
                 where arquivo_id = :arquivo""")
                .param("arquivo", fileId).param("indexacao", indexingId).param("motivo", reason)
                .update();
    }

    /** Repeated request for content already indexed: updates only the document data and shows it in search again. */
    public void confirmUnchanged(IndexFileMessage p) {
        jdbc.sql("""
                update documento_indexado
                   set condominio_id = :condominio, categoria = :categoria, nome_original = :nome, caminho = :caminho,
                       competencia_inicio = :inicio, competencia_fim = :fim, versao_arquivo = :versaoArquivo,
                       retirado = false, indexacao_id = :indexacao, atualizado_em = now()
                 where arquivo_id = :arquivo""")
                .params(requestParams(p))
                .update();
    }

    /** Logical deletion: disappears from search, nothing is deleted. Returns false if the file was never indexed. */
    public boolean withdraw(UUID fileId, UUID indexingId) {
        return jdbc.sql("""
                update documento_indexado set retirado = true, indexacao_id = :indexacao, atualizado_em = now()
                 where arquivo_id = :arquivo""")
                .param("arquivo", fileId).param("indexacao", indexingId)
                .update() > 0;
    }

    /**
     * Replaces, in a single transaction, the file's chunks and vectors with the new ones and stores the document data.
     * No chunks = state sem_texto. {@code vectors} null = embeddings off (keyword search only).
     */
    public void replace(IndexFileMessage p, ChunkedDocument chunked, List<float[]> vectors, String model) {
        replace(p, chunked, vectors, model, null);
    }

    /** {@code warning}: stored in motivo when indexed (e.g. no vectors because Ollama was down). */
    public void replace(IndexFileMessage p, ChunkedDocument chunked, List<float[]> vectors, String model,
            String warning) {
        if (vectors != null && vectors.size() != chunked.chunks().size()) {
            throw new IllegalArgumentException("Vetores (" + vectors.size() + ") e trechos ("
                    + chunked.chunks().size() + ") não batem");
        }
        transaction.executeWithoutResult(status -> {
            Map<String, Object> data = requestParams(p);
            data.put("estado", chunked.noText() ? "sem_texto" : "indexado");
            data.put("motivo", chunked.noText() ? chunked.noTextReason() : warning);
            data.put("paginas", chunked.pages());
            data.put("trechos", chunked.chunks().size());
            data.put("modelo", vectors == null || chunked.noText() ? null : model);
            data.put("versaoIndexador", TextChunker.VERSION);
            jdbc.sql("""
                    update documento_indexado
                       set condominio_id = :condominio, categoria = :categoria, nome_original = :nome,
                           caminho = :caminho, sha256 = :sha256, competencia_inicio = :inicio, competencia_fim = :fim,
                           versao_arquivo = :versaoArquivo, estado = :estado, motivo = :motivo, paginas = :paginas,
                           trechos = :trechos, modelo_embeddings = :modelo, versao_indexador = :versaoIndexador,
                           retirado = false, indexacao_id = :indexacao, atualizado_em = now()
                     where arquivo_id = :arquivo""")
                    .params(data)
                    .update();
            jdbc.sql("delete from trecho where arquivo_id = :arquivo").param("arquivo", p.fileId()).update();

            List<Chunk> chunks = chunked.chunks();
            List<UUID> ids = chunks.stream().map(t -> chunkId(p.fileId(), p.sha256(), t.sequence())).toList();
            jdbcTemplate.batchUpdate("""
                    insert into trecho (id, arquivo_id, condominio_id, ordem, pagina, aba, linha_inicio, linha_fim,
                        secao, paragrafo_inicio, paragrafo_fim, texto)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", chunks, 200, (ps, t) -> {
                ps.setObject(1, ids.get(t.sequence() - 1));
                ps.setObject(2, p.fileId());
                ps.setObject(3, p.condominiumId());
                ps.setInt(4, t.sequence());
                Integer page = null, startRow = null, endRow = null, paragraphStart = null, paragraphEnd = null;
                String tab = null, section = null;
                switch (t.location()) {
                    case Location.Page l -> page = l.number();
                    case Location.Sheet l -> {
                        tab = l.tab();
                        startRow = l.startRow();
                        endRow = l.endRow();
                    }
                    case Location.Paragraphs l -> {
                        paragraphStart = l.start();
                        paragraphEnd = l.end();
                        section = l.section() == null || l.section().isEmpty() ? null : l.section();
                    }
                }
                ps.setObject(5, page);
                ps.setString(6, tab);
                ps.setObject(7, startRow);
                ps.setObject(8, endRow);
                ps.setString(9, section);
                ps.setObject(10, paragraphStart);
                ps.setObject(11, paragraphEnd);
                ps.setString(12, t.text());
            });
            if (vectors != null && !chunks.isEmpty()) {
                List<Integer> indexes = new ArrayList<>();
                for (int i = 0; i < chunks.size(); i++) {
                    indexes.add(i);
                }
                jdbcTemplate.batchUpdate("""
                        insert into trecho_vetor (trecho_id, modelo, vetor) values (?, ?, cast(? as public.vector))""",
                        indexes, 100, (ps, i) -> {
                            ps.setObject(1, ids.get(i));
                            ps.setString(2, model);
                            ps.setString(3, EmbeddingGenerator.asText(vectors.get(i)));
                        });
            }
        });
    }

    /**
     * Stable id: the same file, content, chunking rule and position always generate the same id. Reindexing without
     * changes does not break old citations.
     */
    public static UUID chunkId(UUID fileId, String sha256, int sequence) {
        String key = fileId + ":" + sha256 + ":" + TextChunker.VERSION + ":" + sequence;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, Object> requestParams(IndexFileMessage p) {
        Map<String, Object> data = new HashMap<>();
        data.put("arquivo", p.fileId());
        data.put("condominio", p.condominiumId());
        data.put("categoria", p.category());
        data.put("nome", p.originalName());
        data.put("caminho", p.path());
        data.put("sha256", p.sha256());
        data.put("inicio", p.periodStart());
        data.put("fim", p.periodEnd());
        data.put("versaoArquivo", p.fileVersion());
        data.put("indexacao", p.indexingId());
        return data;
    }

    // ---------------------------------------------------------------- search

    /** Chunk found by the keyword search, with the ts_rank relevance. */
    public record Hit(UUID id, double relevance) {
    }

    /**
     * Keyword search (Portuguese without accents). websearch_to_tsquery accepts "quoted phrase" and exclusion with -.
     * The slash counts as a space, as in the generated column (V2): "Transporte/Combustível" matches "transporte".
     * Relevance ties are broken by file and chunk sequence (deterministic result).
     */
    public List<Hit> findByKeyword(SearchFilters filters, String text, int limit) {
        var where = new Filter(filters);
        where.params.put("texto", text);
        where.params.put("limite", limit);
        return jdbc.sql("""
                select t.id, ts_rank(t.busca, q) as relevancia
                  from trecho t
                  join documento_indexado d on d.arquivo_id = t.arquivo_id,
                       websearch_to_tsquery('rag.portuguese_unaccent', translate(:texto, '/', ' ')) q
                 where t.busca @@ q and %s
                 order by relevancia desc, t.arquivo_id, t.ordem
                 limit :limite""".formatted(where.sql))
                .params(where.params)
                .query((rs, i) -> new Hit(rs.getObject("id", UUID.class), rs.getDouble("relevancia")))
                .list();
    }

    /**
     * Vector search (cosine distance) with the same filters. pgvector's iterative scan (0.8+) keeps a very selective
     * filter from returning fewer results than requested.
     */
    public List<UUID> findByVector(SearchFilters filters, float[] vector, String model, int limit) {
        return findByVector(filters, vector, model, limit, null);
    }

    /**
     * {@code restrictions}: required phrases and negated terms of the question ({@link
     * br.com.condominioauditoria.rag.search.SearchRestrictions}), applied to the vector candidates as in the keyword
     * search. Null = no restriction.
     */
    public List<UUID> findByVector(SearchFilters filters, float[] vector, String model, int limit,
            String restrictions) {
        if (!MODEL_NAME.matcher(model).matches()) {
            throw new IllegalArgumentException("Nome de modelo inválido: " + model);
        }
        var where = new Filter(filters);
        where.params.put("vetor", EmbeddingGenerator.asText(vector));
        where.params.put("limite", limit);
        String restrictionCondition = "";
        if (restrictions != null && !restrictions.isBlank()) {
            // A restriction with stop words only (e.g. -de) becomes an empty tsquery: restricts nothing
            String query = "websearch_to_tsquery('rag.portuguese_unaccent', translate(:restricoes, '/', ' '))";
            restrictionCondition = " and (numnode(" + query + ") = 0 or t.busca @@ " + query + ")";
            where.params.put("restricoes", restrictions);
        }
        // Literal model (validated above) so the planner can use that model's partial HNSW index
        String sql = """
                select t.id
                  from trecho t
                  join documento_indexado d on d.arquivo_id = t.arquivo_id
                  join trecho_vetor v on v.trecho_id = t.id and v.modelo = '%s'
                 where %s%s
                 order by v.vetor <=> cast(:vetor as public.vector)
                 limit :limite""".formatted(model, where.sql, restrictionCondition);
        return transaction.execute(status -> {
            jdbc.sql("select set_config('hnsw.iterative_scan', 'strict_order', true)").query().singleValue();
            jdbc.sql("select set_config('hnsw.ef_search', '100', true)").query().singleValue();
            return jdbc.sql(sql).params(where.params).query((rs, i) -> rs.getObject("id", UUID.class)).list();
        });
    }

    /** Data to show and cite the chunks, in the requested order. An id that no longer exists is left out. */
    public List<FoundChunk> load(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, FoundChunk> byId = new HashMap<>();
        jdbc.sql("""
                select t.id, t.arquivo_id, d.nome_original, d.categoria, d.sha256, t.pagina, t.aba, t.linha_inicio,
                       t.linha_fim, t.secao, t.paragrafo_inicio, t.paragrafo_fim, t.texto
                  from trecho t join documento_indexado d on d.arquivo_id = t.arquivo_id
                 where t.id in (:ids)""")
                .param("ids", ids)
                .query((rs, i) -> foundChunk(rs))
                .list()
                .forEach(t -> byId.put(t.chunkId(), t));
        Map<UUID, FoundChunk> sorted = new LinkedHashMap<>();
        for (UUID id : ids) {
            if (byId.containsKey(id)) {
                sorted.put(id, byId.get(id));
            }
        }
        return List.copyOf(sorted.values());
    }

    private static FoundChunk foundChunk(ResultSet rs) throws SQLException {
        Location local;
        if (rs.getObject("pagina") != null) {
            local = new Location.Page(rs.getInt("pagina"));
        } else if (rs.getString("aba") != null) {
            local = new Location.Sheet(rs.getString("aba"), rs.getInt("linha_inicio"), rs.getInt("linha_fim"));
        } else {
            String section = rs.getString("secao");
            local = new Location.Paragraphs(rs.getInt("paragrafo_inicio"), rs.getInt("paragrafo_fim"),
                    section == null ? "" : section);
        }
        return new FoundChunk(rs.getObject("id", UUID.class), rs.getObject("arquivo_id", UUID.class),
                rs.getString("nome_original"), rs.getString("categoria"), local, rs.getString("texto"), 0,
                rs.getString("sha256"));
    }

    /**
     * WHERE shared by both searches, applied before ordering (ADR 0003): condominium required, only current and not
     * withdrawn, and the optional category, period and file filters.
     */
    private static final class Filter {
        private final Map<String, Object> params = new HashMap<>();
        private final String sql;

        public Filter(SearchFilters f) {
            List<String> conditions = new ArrayList<>();
            conditions.add("d.condominio_id = :condominio");
            conditions.add("d.retirado = false");
            conditions.add("d.vigente = true");
            params.put("condominio", f.condominiumId());
            if (!f.categories().isEmpty()) {
                conditions.add("d.categoria in (:categorias)");
                params.put("categorias", f.categories());
            }
            if (!f.fileIds().isEmpty()) {
                conditions.add("d.arquivo_id in (:arquivos)");
                params.put("arquivos", f.fileIds());
            }
            // Period: the document's reference period overlaps the interval; a document without one is left out
            if (f.dateFrom() != null || f.dateTo() != null) {
                conditions.add("coalesce(d.competencia_inicio, d.competencia_fim) is not null");
            }
            if (f.dateTo() != null) {
                conditions.add("coalesce(d.competencia_inicio, d.competencia_fim) <= :dataFim");
                params.put("dataFim", Date.valueOf(f.dateTo()));
            }
            if (f.dateFrom() != null) {
                conditions.add("coalesce(d.competencia_fim, d.competencia_inicio) >= :dataInicio");
                params.put("dataInicio", Date.valueOf(f.dateFrom()));
            }
            this.sql = String.join(" and ", conditions);
        }
    }

    /** Search filters; empty lists and null dates = no filter. */
    public record SearchFilters(UUID condominiumId, List<String> categories, LocalDate dateFrom, LocalDate dateTo,
            List<UUID> fileIds) {

        public SearchFilters {
            if (condominiumId == null) {
                throw new IllegalArgumentException("Condomínio é obrigatório na busca");
            }
            categories = categories == null ? List.of() : List.copyOf(categories);
            fileIds = fileIds == null ? List.of() : List.copyOf(fileIds);
        }
    }
}
