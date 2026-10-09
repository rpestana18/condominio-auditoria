package br.com.condominioauditoria.api.dto.response.file;

import br.com.condominioauditoria.api.model.enums.IndexingStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Indexing status for the document search. Null = the file was never sent to the index. */
public record IndexingResponse(
        @JsonProperty("situacao") IndexingStatus status,
        @JsonProperty("motivo") String reason,
        @JsonProperty("paginas") Integer pages,
        @JsonProperty("trechos") Integer chunks) {
}
