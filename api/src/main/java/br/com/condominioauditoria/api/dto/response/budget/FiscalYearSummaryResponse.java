package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/** View 1 of RF-11.6. Numbers are null when the fiscal year has no data for them (printed column, no cash flow). */
public record FiscalYearSummaryResponse(
        @JsonProperty("exercicioId") String fiscalYearId,
        @JsonProperty("previstoMes") BigDecimal monthlyPlanned,
        @JsonProperty("previstoExercicio") BigDecimal fiscalYearPlanned,
        @JsonProperty("mesesComFluxo") int monthsWithCashFlow,
        @JsonProperty("previsto") BigDecimal planned,
        @JsonProperty("realizado") BigDecimal actual,
        @JsonProperty("execucao") BigDecimal execution,
        @JsonProperty("maiorExcesso") MonthOverrunResponse largestOverrun,
        @JsonProperty("mesesAcimaDoLimite") Integer monthsAboveLimit,
        @JsonProperty("achadosAbertos") Integer openFindings,
        @JsonProperty("provisorio") boolean provisional,
        @JsonProperty("variacaoPrevistoMes") VariationResponse monthlyPlannedVariation,
        @JsonProperty("variacaoRealizado") VariationResponse actualVariation,
        @JsonProperty("alvo") String target) {
}
