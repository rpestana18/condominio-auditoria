package br.com.condominioauditoria.api.dto.response.budget;

import java.math.BigDecimal;

/** View 1 of RF-11.6. Numbers are null when the fiscal year has no data for them (printed column, no cash flow). */
public record FiscalYearSummaryResponse(
        String fiscalYearId,
        BigDecimal monthlyPlanned,
        BigDecimal fiscalYearPlanned,
        int monthsWithCashFlow,
        BigDecimal planned,
        BigDecimal actual,
        BigDecimal execution,
        MonthOverrunResponse largestOverrun,
        Integer monthsAboveLimit,
        Integer openFindings,
        boolean provisional,
        VariationResponse monthlyPlannedVariation,
        VariationResponse actualVariation,
        String target) {
}
