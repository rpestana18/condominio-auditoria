package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.FiscalYearType;
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
        FiscalYearType type,
        String label,
        UUID budgetId,
        Integer version,
        String start,
        String end,
        BudgetExtensionResponse extension,
        BigDecimal monthlyPlanned,
        List<FiscalYearMonthSummaryResponse> months,
        AccountMappingSummaryResponse mapping,
        BudgetItemSummaryResponse budgetItems,
        String printedColumn,
        List<String> warnings) {
}
