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
 *
 * <p>{@code lineReview}: review state of the budget lines (RF-13.12; ADR 0009). Here the numbers stay (they are the
 * base of the findings and the export); the screen shows the pending step while the lines are not reviewed.
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
        List<BudgetVsActualWarningResponse> warnings,
        LineReviewStateResponse lineReview) {

    /** Without the review state (filled in by the query service, ADR 0009 Decision 1). */
    public BudgetVsActualResponse(String calculationVersion, String period, BudgetVsActualStatus status, String message,
            BudgetBriefResponse budget, List<FiscalYearMonthResponse> months, List<String> summedMonths,
            List<String> monthsWithoutCashFlow, List<String> monthsWithTwoCashFlows,
            PeriodMappingSummaryResponse mapping, boolean provisional, BudgetVsActualTotalsResponse totals,
            List<BudgetVsActualGroupResponse> groups, EntryBlockResponse adjustments, EntryBlockResponse toReallocate,
            EntryBlockResponse withoutBudgetLine, CashFlowCheckResponse cashFlowCheck, Rule20Response rule20,
            List<FundResultResponse> funds, List<BudgetVsActualWarningResponse> warnings) {
        this(calculationVersion, period, status, message, budget, months, summedMonths, monthsWithoutCashFlow,
                monthsWithTwoCashFlows, mapping, provisional, totals, groups, adjustments, toReallocate,
                withoutBudgetLine, cashFlowCheck, rule20, funds, warnings, null);
    }

    /** The same result with the review state of the budget lines; the numbers do not change. */
    public BudgetVsActualResponse withLineReview(LineReviewStateResponse review) {
        return new BudgetVsActualResponse(calculationVersion, period, status, message, budget, months, summedMonths,
                monthsWithoutCashFlow, monthsWithTwoCashFlows, mapping, provisional, totals, groups, adjustments,
                toReallocate, withoutBudgetLine, cashFlowCheck, rule20, funds, warnings, review);
    }
}
