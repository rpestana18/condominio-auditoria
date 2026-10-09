package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Suggestions created for the accounts without mapping, by source, and the accounts left without one. */
public record AccountMappingSuggestionsResponse(
        @JsonProperty("criadas") int created,
        @JsonProperty("daVersaoAnterior") int fromPreviousVersion,
        @JsonProperty("peloNome") int byName,
        @JsonProperty("semSugestao") List<AccountWithoutSuggestionResponse> withoutSuggestion) {
}
