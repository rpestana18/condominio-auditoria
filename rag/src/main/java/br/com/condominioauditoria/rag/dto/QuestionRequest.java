package br.com.condominioauditoria.rag.dto;

import br.com.condominioauditoria.rag.repository.IndexRepository;
import br.com.condominioauditoria.rag.search.DocumentSearch;
import java.util.List;

/**
 * A chat question, already translated from the gRPC contract. Nothing here is read from a database by the rag: the
 * provider, the model and the encrypted key come resolved by the api (ADR 0003, Decision 4).
 *
 * @param filters search filters, applied before the search (RF-04.3, RF-04.10)
 * @param requestedMode KEYWORD when the assistant's embeddings are off
 * @param encryptedKey envelope of the condominium's API key (only the rag decrypts it, at call time)
 * @param authorization the "authorization" metadata of this request, passed on to the numeric tools
 */
public record QuestionRequest(IndexRepository.SearchFilters filters, String question, List<Exchange> history,
        DocumentSearch.Mode requestedMode, String provider, String model, byte[] encryptedKey, int chunkLimit,
        String authorization) {

    public record Exchange(String question, String answer) {
    }

    public static final int MAX_CHARS = 2000;
    /** Chunks offered to the model: 0 = default 8, above 20 is reduced to 20 (assistant.proto). */
    public static final int DEFAULT_CHUNKS = 8;
    public static final int MAX_CHUNKS = 20;
}
