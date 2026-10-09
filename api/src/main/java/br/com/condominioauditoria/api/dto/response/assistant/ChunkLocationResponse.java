package br.com.condominioauditoria.api.dto.response.assistant;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Where a chunk is in its file: page, sheet rows or paragraphs, plus a description for the screen. */
public record ChunkLocationResponse(
        @JsonProperty("tipo") String type,
        @JsonProperty("pagina") Integer page,
        @JsonProperty("aba") String sheet,
        @JsonProperty("linhaInicio") Integer startRow,
        @JsonProperty("linhaFim") Integer endRow,
        @JsonProperty("paragrafoInicio") Integer startParagraph,
        @JsonProperty("paragrafoFim") Integer endParagraph,
        @JsonProperty("secao") String section,
        @JsonProperty("descricao") String description) {
}
