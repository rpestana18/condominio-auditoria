package br.com.condominioauditoria.api.messaging;

import br.com.condominioauditoria.api.model.file.SourceFile;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Message api → rag (queue rag.indexing). Contract: contracts/mensagens/v3/index-file.schema.json. The file
 * content is not in the message: the rag reads the original by its path.
 *
 * Delivery 1: the file's period and version do not exist in the api yet (they go as null); embedding mode and model are
 * left out of the message when null (= LOCAL with the rag's default model) until the AI configuration per condominium
 * (delivery 2).
 */
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
        @JsonInclude(JsonInclude.Include.NON_NULL) String embeddingMode,
        @JsonInclude(JsonInclude.Include.NON_NULL) String embeddingModel) {

    public enum Operation {
        INDEX, WITHDRAW
    }

    static IndexFileMessage index(SourceFile a) {
        return new IndexFileMessage(3, Operation.INDEX, a.getIndexingId(), a.getId(), a.getCondominiumId(),
                a.getCategory().name(), a.getOriginalName(), a.getPath(), a.getSha256(), null, null, null, null,
                null);
    }
}
