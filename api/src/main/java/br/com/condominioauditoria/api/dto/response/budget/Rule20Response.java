package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

/**
 * The 20% rule (monthly only). {@code limit} rounded to cents; the comparison is exact (MonthlyOverrunRule).
 * {@code maxScenario} = overrun + to reallocate + without budget line (Q26).
 */
public record Rule20Response(
        @JsonProperty("regra") String rule,
        @JsonProperty("versaoRegra") String ruleVersion,
        @JsonProperty("limitePercentual") BigDecimal limitPercentage,
        @JsonProperty("previstoMes") BigDecimal monthlyPlanned,
        @JsonProperty("excesso") BigDecimal overrun,
        @JsonProperty("percentual") BigDecimal percentage,
        @JsonProperty("limite") BigDecimal limit,
        @JsonProperty("linhasAcima") int linesAbove,
        @JsonProperty("linhas") List<OverrunLineResponse> lines,
        @JsonProperty("aRealocar") BigDecimal toReallocate,
        @JsonProperty("semLinhaPo") BigDecimal withoutBudgetLine,
        @JsonProperty("cenarioMaximo") BigDecimal maxScenario,
        @JsonProperty("percentualCenarioMaximo") BigDecimal maxScenarioPercentage,
        @JsonProperty("provisorio") boolean provisional,
        @JsonProperty("acimaDoLimite") boolean aboveLimit) {
}
