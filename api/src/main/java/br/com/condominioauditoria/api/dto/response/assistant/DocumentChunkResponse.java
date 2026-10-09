package br.com.condominioauditoria.api.dto.response.assistant;

import br.com.condominioauditoria.api.model.enums.FileCategory;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** A citable chunk (TrechoDocumento). The text is a literal, unchecked transcription of the document. */
public record DocumentChunkResponse(
        @JsonProperty("trechoId") String chunkId,
        @JsonProperty("arquivoId") UUID fileId,
        @JsonProperty("nomeArquivo") String fileName,
        @JsonProperty("categoria") FileCategory category,
        @JsonProperty("localizacao") ChunkLocationResponse location,
        @JsonProperty("texto") String text,
        String sha256) {
}
