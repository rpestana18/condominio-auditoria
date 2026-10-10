package br.com.condominioauditoria.api.dto.response.assistant;

import br.com.condominioauditoria.api.model.enums.FileCategory;
import java.util.UUID;

/** CitacaoDocumento: a chunk plus its citation number (from 1). */
public record DocumentCitationResponse(
        int number,
        String chunkId,
        UUID fileId,
        String fileName,
        FileCategory category,
        ChunkLocationResponse location,
        String text,
        String sha256) {
}
