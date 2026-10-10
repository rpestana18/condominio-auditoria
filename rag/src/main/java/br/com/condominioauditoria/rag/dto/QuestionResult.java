package br.com.condominioauditoria.rag.dto;

import br.com.condominioauditoria.rag.search.FoundChunk;
import java.util.List;

/**
 * Answer ready and already checked, the way it goes into the last event of the {@code Ask} stream.
 *
 * @param citedChunks without repetition, in the order of first citation (the api numbers the citations in that order)
 * @param warning empty when there is nothing to warn about (model refusal, keyword-only search)
 */
public record QuestionResult(Status status, List<Paragraph> inDocuments, List<DataItem> inStoredData,
        List<FoundChunk> citedChunks, String suggestion, String warning, Usage usage) {

    public enum Status {
        ANSWERED, NOT_FOUND
    }

    public record Paragraph(String text, List<String> chunkIds) {
    }

    /** Block built by the rag from the tool, with the model's comment (no numbers). */
    public record DataItem(QueriedData queried, String comment) {
    }

    /** Usage of this question, summed over all attempts and calls to the provider (RF-09.7). */
    public record Usage(long inputTokens, long outputTokens, String provider, String model, String promptVersion,
            int attempts) {
    }

    /** Standard refusal of RF-04.12. */
    public static final String NOT_FOUND_TEXT = "Não encontrei nos documentos.";
}
