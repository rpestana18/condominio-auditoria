package br.com.condominioauditoria.rag.messaging;

import java.util.UUID;

/** Message api → rag. Contract: contracts/mensagens/v3/file-received.schema.json. */
public record FileReceivedMessage(
        int version,
        UUID processingId,
        UUID fileId,
        UUID condominiumId,
        String category,
        String originalName,
        String path,
        String sha256) {
}
