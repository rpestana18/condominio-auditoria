package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Line counts of the budget items screen, by status. */
public record BudgetItemSummaryResponse(
        @JsonProperty("linhas") int lines,
        @JsonProperty("confirmadas") int confirmed,
        @JsonProperty("sugeridas") int suggested,
        @JsonProperty("recusadas") int rejected,
        @JsonProperty("semRubrica") int withoutItem) {
}
