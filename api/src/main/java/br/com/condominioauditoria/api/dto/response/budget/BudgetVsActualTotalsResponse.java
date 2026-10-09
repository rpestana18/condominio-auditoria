package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * @param planned planned amount of the period (monthly planned × summed months)
 * @param actualExpense in lines + to reallocate + without budget line (no adjustments and no transfers)
 * @param fiscalYearPlanned reference: monthly planned × months of the fiscal year
 */
public record BudgetVsActualTotalsResponse(
        @JsonProperty("previstoMes") BigDecimal monthlyPlanned,
        @JsonProperty("previsto") BigDecimal planned,
        @JsonProperty("despesaRealizada") BigDecimal actualExpense,
        @JsonProperty("emLinhas") BigDecimal inLines,
        @JsonProperty("diferenca") BigDecimal difference,
        @JsonProperty("execucao") BigDecimal execution,
        @JsonProperty("previstoExercicio") BigDecimal fiscalYearPlanned) {
}
