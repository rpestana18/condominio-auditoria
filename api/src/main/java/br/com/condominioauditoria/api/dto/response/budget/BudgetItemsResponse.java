package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

/** Budget items screen of a budget version (contracts/openapi.yaml). */
public record BudgetItemsResponse(
        @JsonProperty("previsaoId") UUID budgetId,
        @JsonProperty("versao") Integer version,
        @JsonProperty("resumo") BudgetItemSummaryResponse summary,
        @JsonProperty("linhas") List<BudgetLineItemResponse> lines) {
}
