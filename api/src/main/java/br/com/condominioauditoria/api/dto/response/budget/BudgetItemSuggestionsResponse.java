package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * {@code firstBudget}: the condominium had no budget items, and each line of this budget became an already confirmed
 * item.
 */
public record BudgetItemSuggestionsResponse(
        @JsonProperty("primeiraPo") boolean firstBudget,
        @JsonProperty("rubricasCriadas") int createdItems,
        @JsonProperty("sugeridas") int suggested,
        @JsonProperty("daVersaoAnterior") int fromPreviousVersion,
        @JsonProperty("pelaConta") int byAccount,
        @JsonProperty("semSugestao") List<LineWithoutSuggestionResponse> withoutSuggestion) {
}
