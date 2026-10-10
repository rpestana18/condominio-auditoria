package br.com.condominioauditoria.api.messaging;

import java.util.UUID;

/**
 * Message rag → api (queue api.indexing-results). Contract: contracts/mensagens/v3/indexing-result.schema.json. These
 * records belong to the api: the rag has its own. Only the JSON contract is shared.
 */
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
}
