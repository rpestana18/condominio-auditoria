package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.MonthStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A month of the fiscal year (the screen shows all 12). Numbers only exist with one cash flow, and only one.
 * {@code extended}: month after the fiscal year in which the budget is valid by extension (RF-11.3); in the
 * cumulative view it comes after the 12, outside the sum. {@code partialUntil}: partial month (RF-13.10), with entries
 * until this date; outside the cumulative and the 20% rule.
 */
public record FiscalYearMonthResponse(
        String month,
        MonthStatus status,
        List<UsedCashFlowResponse> cashFlows,
        BigDecimal planned,
        BigDecimal actualExpense,
        BigDecimal overrun,
        BigDecimal overrunPercentage,
        Boolean aboveLimit,
        boolean extended,
        LocalDate partialUntil) {

    public FiscalYearMonthResponse withExtended(boolean amount) {
        return new FiscalYearMonthResponse(month, status, cashFlows, planned, actualExpense, overrun, overrunPercentage,
                aboveLimit, amount, partialUntil);
    }

    /** Partial month (RF-13.10): keeps its numbers, without the 20% rule. */
    public FiscalYearMonthResponse withPartialUntil(LocalDate until) {
        return new FiscalYearMonthResponse(month, status, cashFlows, planned, actualExpense, overrun, overrunPercentage,
                until == null ? aboveLimit : null, extended, until);
    }
}
