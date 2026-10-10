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
 * SQL of the index in the rag schema (ADR 0003, Decision 3): indexed_document, chunk and chunk_vector. No JPA and
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
                select file_id, sha256, status, reason, pages, chunks, embedding_model, indexer_version, withdrawn
                  from indexed_document where file_id = :fileId""")
                .param("fileId", fileId)
                .query((rs, i) -> new IndexedDocument(rs.getObject("file_id", UUID.class), rs.getString("sha256"),
                        rs.getString("status"), rs.getString("reason"), (Integer) rs.getObject("pages"),
                        (Integer) rs.getObject("chunks"), rs.getString("embedding_model"),
                        rs.getString("indexer_version"), rs.getBoolean("withdrawn")))
                .optional();
    }

    /** Start of the work. A new row is born with the request data; an existing row only changes state (see class). */
    public void markIndexing(IndexFileMessage p) {
        jdbc.sql("""
                insert into indexed_document (file_id, condominium_id, category, original_name, path, sha256,
                    period_start, period_end, file_version, status, indexing_id)
                values (:fileId, :condominiumId, :category, :name, :path, :sha256, :start, :end, :fileVersion,
                    'indexando', :indexingId)
                on conflict (file_id) do update
                   set status = 'indexando', reason = null, indexing_id = excluded.indexing_id, updated_at = now()""")
                .params(requestParams(p))
                .update();
    }

    public void markError(UUID fileId, UUID indexingId, String reason) {
        jdbc.sql("""
                update indexed_document set status = 'erro', reason = :reason, indexing_id = :indexingId,
                       updated_at = now()
                 where file_id = :fileId""")
                .param("fileId", fileId).param("indexingId", indexingId).param("reason", reason)
                .update();
    }

    /** Repeated request for content already indexed: updates only the document data and shows it in search again. */
    public void confirmUnchanged(IndexFileMessage p) {
        jdbc.sql("""
                update indexed_document
                   set condominium_id = :condominiumId, category = :category, original_name = :name, path = :path,
                       period_start = :start, period_end = :end, file_version = :fileVersion,
                       withdrawn = false, indexing_id = :indexingId, updated_at = now()
                 where file_id = :fileId""")
                .params(requestParams(p))
                .update();
    }

    /** Logical deletion: disappears from search, nothing is deleted. Returns false if the file was never indexed. */
    public boolean withdraw(UUID fileId, UUID indexingId) {
        return jdbc.sql("""
                update indexed_document set withdrawn = true, indexing_id = :indexingId, updated_at = now()
                 where file_id = :fileId""")
                .param("fileId", fileId).param("indexingId", indexingId)
                .update() > 0;
    }

    /**
     * Replaces, in a single transaction, the file's chunks and vectors with the new ones and stores the document data.
     * No chunks = state sem_texto. {@code vectors} null = embeddings off (keyword search only).
     */
    public void replace(IndexFileMessage p, ChunkedDocument chunked, List<float[]> vectors, String model) {
        replace(p, chunked, vectors, model, null);
    }

    /** {@code warning}: stored in reason when indexed (e.g. no vectors because Ollama was down). */
    public void replace(IndexFileMessage p, ChunkedDocument chunked, List<float[]> vectors, String model,
            String warning) {
        if (vectors != null && vectors.size() != chunked.chunks().size()) {
            throw new IllegalArgumentException("Vetores (" + vectors.size() + ") e trechos ("
                    + chunked.chunks().size() + ") não batem");
        }
        transaction.executeWithoutResult(status -> {
            Map<String, Object> data = requestParams(p);
            data.put("status", chunked.noText() ? "sem_texto" : "indexado");
            data.put("reason", chunked.noText() ? chunked.noTextReason() : warning);
            data.put("pages", chunked.pages());
            data.put("chunks", chunked.chunks().size());
            data.put("model", vectors == null || chunked.noText() ? null : model);
            data.put("indexerVersion", TextChunker.VERSION);
            jdbc.sql("""
                    update indexed_document
                       set condominium_id = :condominiumId, category = :category, original_name = :name,
                           path = :path, sha256 = :sha256, period_start = :start, period_end = :end,
                           file_version = :fileVersion, status = :status, reason = :reason, pages = :pages,
                           chunks = :chunks, embedding_model = :model, indexer_version = :indexerVersion,
                           withdrawn = false, indexing_id = :indexingId, updated_at = now()
                     where file_id = :fileId""")
                    .params(data)
                    .update();
            jdbc.sql("delete from chunk where file_id = :fileId").param("fileId", p.fileId()).update();

            List<Chunk> chunks = chunked.chunks();
            List<UUID> ids = chunks.stream().map(t -> chunkId(p.fileId(), p.sha256(), t.sequence())).toList();
            jdbcTemplate.batchUpdate("""
                    insert into chunk (id, file_id, condominium_id, sequence, page, tab, start_row, end_row,
                        section, paragraph_start, paragraph_end, text)
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
                        insert into chunk_vector (chunk_id, model, embedding)
                        values (?, ?, cast(? as public.vector))""",
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
        data.put("fileId", p.fileId());
        data.put("condominiumId", p.condominiumId());
        data.put("category", p.category());
        data.put("name", p.originalName());
        data.put("path", p.path());
        data.put("sha256", p.sha256());
        data.put("start", p.periodStart());
        data.put("end", p.periodEnd());
        data.put("fileVersion", p.fileVersion());
        data.put("indexingId", p.indexingId());
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
        where.params.put("text", text);
        where.params.put("limit", limit);
        return jdbc.sql("""
                select t.id, ts_rank(t.search_vector, q) as relevance
                  from chunk t
                  join indexed_document d on d.file_id = t.file_id,
                       websearch_to_tsquery('rag.portuguese_unaccent', translate(:text, '/', ' ')) q
                 where t.search_vector @@ q and %s
                 order by relevance desc, t.file_id, t.sequence
                 limit :limit""".formatted(where.sql))
                .params(where.params)
                .query((rs, i) -> new Hit(rs.getObject("id", UUID.class), rs.getDouble("relevance")))
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
        where.params.put("embedding", EmbeddingGenerator.asText(vector));
        where.params.put("limit", limit);
        String restrictionCondition = "";
        if (restrictions != null && !restrictions.isBlank()) {
            // A restriction with stop words only (e.g. -de) becomes an empty tsquery: restricts nothing
            String query = "websearch_to_tsquery('rag.portuguese_unaccent', translate(:restrictions, '/', ' '))";
            restrictionCondition = " and (numnode(" + query + ") = 0 or t.search_vector @@ " + query + ")";
            where.params.put("restrictions", restrictions);
        }
        // Literal model (validated above) so the planner can use that model's partial HNSW index
        String sql = """
                select t.id
                  from chunk t
                  join indexed_document d on d.file_id = t.file_id
                  join chunk_vector v on v.chunk_id = t.id and v.model = '%s'
                 where %s%s
                 order by v.embedding <=> cast(:embedding as public.vector)
                 limit :limit""".formatted(model, where.sql, restrictionCondition);
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
                select t.id, t.file_id, d.original_name, d.category, d.sha256, t.page, t.tab, t.start_row,
                       t.end_row, t.section, t.paragraph_start, t.paragraph_end, t.text
                  from chunk t join indexed_document d on d.file_id = t.file_id
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
        if (rs.getObject("page") != null) {
            local = new Location.Page(rs.getInt("page"));
        } else if (rs.getString("tab") != null) {
            local = new Location.Sheet(rs.getString("tab"), rs.getInt("start_row"), rs.getInt("end_row"));
        } else {
            String section = rs.getString("section");
            local = new Location.Paragraphs(rs.getInt("paragraph_start"), rs.getInt("paragraph_end"),
                    section == null ? "" : section);
        }
        return new FoundChunk(rs.getObject("id", UUID.class), rs.getObject("file_id", UUID.class),
                rs.getString("original_name"), rs.getString("category"), local, rs.getString("text"), 0,
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
            conditions.add("d.condominium_id = :condominiumId");
            conditions.add("d.withdrawn = false");
            conditions.add("d.is_current = true");
            params.put("condominiumId", f.condominiumId());
            if (!f.categories().isEmpty()) {
                conditions.add("d.category in (:categories)");
                params.put("categories", f.categories());
            }
            if (!f.fileIds().isEmpty()) {
                conditions.add("d.file_id in (:fileIds)");
                params.put("fileIds", f.fileIds());
            }
            // Period: the document's reference period overlaps the interval; a document without one is left out
            if (f.dateFrom() != null || f.dateTo() != null) {
                conditions.add("coalesce(d.period_start, d.period_end) is not null");
            }
            if (f.dateTo() != null) {
                conditions.add("coalesce(d.period_start, d.period_end) <= :dateTo");
                params.put("dateTo", Date.valueOf(f.dateTo()));
            }
            if (f.dateFrom() != null) {
                conditions.add("coalesce(d.period_end, d.period_start) >= :dateFrom");
                params.put("dateFrom", Date.valueOf(f.dateFrom()));
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
