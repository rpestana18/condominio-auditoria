package br.com.condominioauditoria.api.dto.response.budget;

import java.util.List;
import java.util.UUID;

/** Budget items screen of a budget version (contracts/openapi.yaml). */
public record BudgetItemsResponse(
        UUID budgetId,
        Integer version,
        BudgetItemSummaryResponse summary,
        List<BudgetLineItemResponse> lines) {
}
