package br.com.condominioauditoria.api.dto.response.assistant;

import br.com.condominioauditoria.api.model.enums.FileCategory;
import java.util.UUID;

/** A citable chunk (DocumentChunkResponse). The text is a literal, unchecked transcription of the document. */
public record DocumentChunkResponse(
        String chunkId,
        UUID fileId,
        String fileName,
        FileCategory category,
        ChunkLocationResponse location,
        String text,
        String sha256) {
}
