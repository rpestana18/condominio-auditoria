package br.com.condominioauditoria.api.dto.response.budget;

import java.math.BigDecimal;

/**
 * @param planned planned amount of the period (monthly planned × summed months)
 * @param actualExpense in lines + to reallocate + without budget line (no adjustments and no transfers)
 * @param fiscalYearPlanned reference: monthly planned × months of the fiscal year
 */
public record BudgetVsActualTotalsResponse(
        BigDecimal monthlyPlanned,
        BigDecimal planned,
        BigDecimal actualExpense,
        BigDecimal inLines,
        BigDecimal difference,
        BigDecimal execution,
        BigDecimal fiscalYearPlanned) {
}
