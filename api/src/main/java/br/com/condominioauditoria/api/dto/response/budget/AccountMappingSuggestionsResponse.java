package br.com.condominioauditoria.api.dto.response.budget;

import java.util.List;

/** Suggestions created for the accounts without mapping, by source, and the accounts left without one. */
public record AccountMappingSuggestionsResponse(
        int created,
        int fromPreviousVersion,
        int byName,
        List<AccountWithoutSuggestionResponse> withoutSuggestion) {
}
