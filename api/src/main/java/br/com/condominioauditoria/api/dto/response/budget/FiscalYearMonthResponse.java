package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.MonthStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

/**
 * A month of the fiscal year (the screen shows all 12). Numbers only exist with one cash flow, and only one.
 * {@code extended}: month after the fiscal year in which the budget is valid by extension (RF-11.3); in the
 * cumulative view it comes after the 12, outside the sum.
 */
public record FiscalYearMonthResponse(
        @JsonProperty("mes") String month,
        @JsonProperty("situacao") MonthStatus status,
        @JsonProperty("fluxos") List<UsedCashFlowResponse> cashFlows,
        @JsonProperty("previsto") BigDecimal planned,
        @JsonProperty("despesaRealizada") BigDecimal actualExpense,
        @JsonProperty("excesso") BigDecimal overrun,
        @JsonProperty("percentualExcesso") BigDecimal overrunPercentage,
        @JsonProperty("acimaDoLimite") Boolean aboveLimit,
        @JsonProperty("prorrogado") boolean extended) {

    public FiscalYearMonthResponse withExtended(boolean amount) {
        return new FiscalYearMonthResponse(month, status, cashFlows, planned, actualExpense, overrun, overrunPercentage,
                aboveLimit, amount);
    }
}
