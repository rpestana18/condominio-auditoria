package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.BudgetVsActualStatus;
import java.util.List;

/**
 * Budget vs. actual result (RF-03.1.6 to RF-03.1.11), the same object for the screen, the export and the golden test.
 * Immutable and with nothing calculated on read: everything comes from {@link BudgetVsActualCalculator}. Money in
 * BigDecimal with 2 decimals; percentages with 1 decimal (half up), null when the planned amount is zero ("—").
 *
 * <p>With a {@code status} other than CALCULATED, the numbers are null and {@code message} says why: the system never
 * shows zero in place of a number that was not assessed (RF-03.1.10).
 */
public record BudgetVsActualResponse(
        String calculationVersion,
        String period,
        BudgetVsActualStatus status,
        String message,
        BudgetBriefResponse budget,
        List<FiscalYearMonthResponse> months,
        List<String> summedMonths,
        List<String> monthsWithoutCashFlow,
        List<String> monthsWithTwoCashFlows,
        PeriodMappingSummaryResponse mapping,
        boolean provisional,
        BudgetVsActualTotalsResponse totals,
        List<BudgetVsActualGroupResponse> groups,
        EntryBlockResponse adjustments,
        EntryBlockResponse toReallocate,
        EntryBlockResponse withoutBudgetLine,
        CashFlowCheckResponse cashFlowCheck,
        Rule20Response rule20,
        List<FundResultResponse> funds,
        List<BudgetVsActualWarningResponse> warnings) {
}
