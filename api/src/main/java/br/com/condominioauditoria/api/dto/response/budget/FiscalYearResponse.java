package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.FiscalYearType;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A fiscal year of the list. {@code id}: "budget:&lt;uuid&gt;" or "column:&lt;uuid&gt;" (the uuid of the budget that
 * printed the column), used in the comparison. For the printed column, {@code start} and {@code end} are the 12 months
 * before the budget that printed it, {@code months} is empty (no actual) and mapping, budget items and extension are
 * null. {@code printedColumn}: id of the column this fiscal year superseded, kept only as a check (RF-11.5).
 */
public record FiscalYearResponse(
        String id,
        @JsonProperty("tipo") FiscalYearType type,
        @JsonProperty("rotulo") String label,
        @JsonProperty("poId") UUID budgetId,
        @JsonProperty("versao") Integer version,
        @JsonProperty("inicio") String start,
        @JsonProperty("fim") String end,
        @JsonProperty("prorrogacao") BudgetExtensionResponse extension,
        @JsonProperty("previstoMes") BigDecimal monthlyPlanned,
        @JsonProperty("meses") List<FiscalYearMonthSummaryResponse> months,
        @JsonProperty("depara") AccountMappingSummaryResponse mapping,
        @JsonProperty("rubricas") BudgetItemSummaryResponse budgetItems,
        @JsonProperty("colunaImpressa") String printedColumn,
        @JsonProperty("avisos") List<String> warnings) {
}
