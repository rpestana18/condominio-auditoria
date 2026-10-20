package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.MonthStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** A month of the grid. Numbers only with one cash flow. {@code partialUntil}: partial cash flow ("parcial até DD/MM", RF-13.10), shown with its numbers but out of the cumulative, the line balance and the 20% rule ({@code inCumulative} false, {@code overrunPercentage} and {@code aboveLimit} null). */
public record TrackingMonthResponse(
        String month,
        MonthStatus status,
        LocalDate partialUntil,
        boolean inCumulative,
        List<UsedCashFlowResponse> cashFlows,
        BigDecimal planned,
        BigDecimal actualInLines,
        BigDecimal actualExpense,
        BigDecimal difference,
        BigDecimal overrun,
        Integer linesAbove,
        BigDecimal overrunPercentage,
        Boolean aboveLimit) {
}
