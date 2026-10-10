package br.com.condominioauditoria.api.dto.response.budget;


/** Line counts of the budget items screen, by status. */
public record BudgetItemSummaryResponse(
        int lines,
        int confirmed,
        int suggested,
        int rejected,
        int withoutItem) {
}
