package br.com.condominioauditoria.rag.messaging;

import java.time.LocalDate;
import java.util.UUID;

/** Message api → rag (queue rag.indexing). Contract: contracts/mensagens/v3/index-file.schema.json. */
public record IndexFileMessage(
        int version,
        Operation operation,
        UUID indexingId,
        UUID fileId,
        UUID condominiumId,
        String category,
        String originalName,
        String path,
        String sha256,
        LocalDate periodStart,
        LocalDate periodEnd,
        Integer fileVersion,
        EmbeddingMode embeddingMode,
        String embeddingModel) {

    public enum Operation {
        INDEX, WITHDRAW
    }

    /** Missing or null = LOCAL. OFF = only chunks for the keyword search, no vectors. */
    public enum EmbeddingMode {
        LOCAL, OFF
    }

    public boolean withVectors() {
        return embeddingMode != EmbeddingMode.OFF;
    }
}
