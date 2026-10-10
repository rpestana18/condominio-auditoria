package br.com.condominioauditoria.rag.search;

import br.com.condominioauditoria.rag.exception.EmbeddingsUnavailableException;
import br.com.condominioauditoria.rag.repository.IndexRepository;
import br.com.condominioauditoria.rag.repository.IndexRepository.Hit;
import br.com.condominioauditoria.rag.repository.IndexRepository.SearchFilters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Search in the indexed documents (ADR 0003, Decision 3).
 * <ul>
 * <li>KEYWORD: PostgreSQL keyword search only, no AI.</li>
 * <li>HYBRID: keyword (up to 50) + vector (up to 50) joined by rank fusion, k = 60. If Ollama is down, falls back to
 * KEYWORD and says so in the result.</li>
 * </ul>
 * The filters (condominium, validity, logical deletion, category, period, files) go into the WHERE of both searches.
 */
@Service
public class DocumentSearch {

    public static final int DEFAULT_LIMIT = 10;
    public static final int MAX_LIMIT = 50;
    /** Candidates of each search before the fusion. */
    static final int CANDIDATES = 50;

    private static final Logger log = LoggerFactory.getLogger(DocumentSearch.class);

    public enum Mode {
        KEYWORD, HYBRID
    }

    public record Result(List<FoundChunk> chunks, Mode modeUsed) {
    }

    private final IndexRepository repository;
    private final EmbeddingGenerator embeddings;

    DocumentSearch(IndexRepository repository, EmbeddingGenerator embeddings) {
        this.repository = repository;
        this.embeddings = embeddings;
    }

    /** {@code limit} 0 = default (10); above 50 is reduced to 50. */
    public Result search(SearchFilters filters, String text, Mode mode, int limit) {
        int n = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        if (mode == Mode.KEYWORD) {
            return byKeyword(filters, text, n);
        }
        float[] vector;
        try {
            vector = embeddings.generate(text);
        } catch (EmbeddingsUnavailableException error) {
            log.warn("Busca híbrida virou busca por palavra: {}", error.getMessage());
            return byKeyword(filters, text, n);
        }
        List<UUID> words = repository.findByKeyword(filters, text, CANDIDATES).stream().map(Hit::id)
                .toList();
        // Phrases and exclusions of the question also apply to the vector candidates
        List<UUID> vectors = repository.findByVector(filters, vector, embeddings.model(), CANDIDATES,
                SearchRestrictions.extract(text));
        List<RankFusion.Scored> fused = RankFusion.fuse(List.of(words, vectors), RankFusion.K, n);
        return new Result(withScores(fused.stream().map(RankFusion.Scored::id).toList(),
                scores(fused)), Mode.HYBRID);
    }

    private Result byKeyword(SearchFilters filters, String text, int n) {
        List<Hit> hits = repository.findByKeyword(filters, text, n);
        Map<UUID, Double> notes = new HashMap<>();
        hits.forEach(a -> notes.put(a.id(), a.relevance()));
        return new Result(withScores(hits.stream().map(Hit::id).toList(), notes), Mode.KEYWORD);
    }

    private List<FoundChunk> withScores(List<UUID> ids, Map<UUID, Double> notes) {
        return repository.load(ids).stream().map(t -> t.withScore(notes.get(t.chunkId()))).toList();
    }

    private static Map<UUID, Double> scores(List<RankFusion.Scored> fused) {
        Map<UUID, Double> notes = new HashMap<>();
        fused.forEach(p -> notes.put(p.id(), p.score()));
        return notes;
    }
}
