package br.com.condominioauditoria.api.messaging;

import br.com.condominioauditoria.api.model.file.SourceFile;
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

    static FileReceivedMessage from(SourceFile a) {
        return new FileReceivedMessage(3, a.getProcessingId(), a.getId(), a.getCondominiumId(), a.getCategory().name(),
                a.getOriginalName(), a.getPath(), a.getSha256());
    }
}
