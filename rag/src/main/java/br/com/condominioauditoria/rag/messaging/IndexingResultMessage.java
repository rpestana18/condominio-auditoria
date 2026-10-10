package br.com.condominioauditoria.rag.messaging;

import java.util.UUID;

/** Message rag → api (queue api.indexing-results). Contract: contracts/mensagens/v3/indexing-result.schema.json. */
public record IndexingResultMessage(
        int version,
        UUID indexingId,
        UUID fileId,
        UUID condominiumId,
        Status status,
        String reason,
        Integer pages,
        Integer chunks,
        String embeddingModel,
        String indexerVersion) {

    public enum Status {
        INDEXING, INDEXED, NO_TEXT, WITHDRAWN, ERROR
    }

    public static IndexingResultMessage indexing(IndexFileMessage p) {
        return new IndexingResultMessage(3, p.indexingId(), p.fileId(), p.condominiumId(), Status.INDEXING, null,
                null, null, null, null);
    }

    public static IndexingResultMessage indexed(IndexFileMessage p, int pages, int chunks, String model,
            String indexerVersion) {
        return indexed(p, pages, chunks, model, indexerVersion, null);
    }

    /** {@code warning}: e.g. indexed only for the keyword search because Ollama was down (goes in the reason). */
    public static IndexingResultMessage indexed(IndexFileMessage p, int pages, int chunks, String model,
            String indexerVersion, String warning) {
        return new IndexingResultMessage(3, p.indexingId(), p.fileId(), p.condominiumId(), Status.INDEXED, warning,
                pages, chunks, model, indexerVersion);
    }

    public static IndexingResultMessage noText(IndexFileMessage p, String reason, int pages, String indexerVersion) {
        return new IndexingResultMessage(3, p.indexingId(), p.fileId(), p.condominiumId(), Status.NO_TEXT, reason,
                pages, 0, null, indexerVersion);
    }

    public static IndexingResultMessage withdrawn(IndexFileMessage p) {
        return new IndexingResultMessage(3, p.indexingId(), p.fileId(), p.condominiumId(), Status.WITHDRAWN, null,
                null, null, null, null);
    }

    public static IndexingResultMessage error(IndexFileMessage p, String reason) {
        return new IndexingResultMessage(3, p.indexingId(), p.fileId(), p.condominiumId(), Status.ERROR, reason,
                null, null, null, null);
    }
}
