package br.com.condominioauditoria.api.dto.response.budget;

import java.math.BigDecimal;

/** Fiscal year cumulative of the grid: only the complete months with a cash flow, on both sides. */
public record TrackingCumulativeResponse(
        int months,
        BigDecimal planned,
        BigDecimal actualInLines,
        BigDecimal actualExpense,
        BigDecimal balance) {
}
