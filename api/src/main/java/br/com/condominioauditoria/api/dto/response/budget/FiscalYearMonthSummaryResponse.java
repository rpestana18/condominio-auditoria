package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.MonthStatus;

/** Fiscal year month and whether a cash flow is loaded; {@code extended}: month after the fiscal year, by extension. */
public record FiscalYearMonthSummaryResponse(
        String month,
        MonthStatus status,
        boolean extended) {
}
