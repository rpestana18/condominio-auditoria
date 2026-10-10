package br.com.condominioauditoria.api.dto.response.budget;

import java.util.List;

/**
 * {@code firstBudget}: the condominium had no budget items, and each line of this budget became an already confirmed
 * item.
 */
public record BudgetItemSuggestionsResponse(
        boolean firstBudget,
        int createdItems,
        int suggested,
        int fromPreviousVersion,
        int byAccount,
        List<LineWithoutSuggestionResponse> withoutSuggestion) {
}
