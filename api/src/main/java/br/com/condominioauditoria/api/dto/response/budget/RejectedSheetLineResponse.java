package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Sheet line not loaded: line number, content and reason. */
public record RejectedSheetLineResponse(
        @JsonProperty("linha") int line,
        @JsonProperty("conteudo") String content,
        @JsonProperty("motivo") String reason) {
}
