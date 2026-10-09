package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Cash flow account left without a suggestion, with the reason. */
public record AccountWithoutSuggestionResponse(
        @JsonProperty("conta") String account,
        @JsonProperty("nome") String name,
        @JsonProperty("motivo") String reason) {
}
