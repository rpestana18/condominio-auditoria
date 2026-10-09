package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.MonthStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Fiscal year month and whether a cash flow is loaded; {@code extended}: month after the fiscal year, by extension. */
public record FiscalYearMonthSummaryResponse(
        @JsonProperty("mes") String month,
        @JsonProperty("situacao") MonthStatus status,
        @JsonProperty("prorrogado") boolean extended) {
}
