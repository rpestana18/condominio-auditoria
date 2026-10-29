package br.com.condominioauditoria.api.dto.response.budget;

import java.util.List;

/**
 * Automatic links created or changed (Q54), by source, how many of them are of low confidence, and the accounts left
 * without a link found.
 */
public record AccountMappingSuggestionsResponse(
        int created,
        int fromPreviousVersion,
        int fromSheet,
        int fromBudgetSheet,
        int byName,
        int lowConfidence,
        List<AccountWithoutSuggestionResponse> withoutSuggestion) {
}
