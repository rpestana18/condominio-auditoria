package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.MonthStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/** Chart 2: monthly overrun as a % of planned, limit and maximum scenario (RF-03.1.11). */
public record Rule20PointResponse(
        @JsonProperty("mes") String month,
        @JsonProperty("situacao") MonthStatus status,
        @JsonProperty("excesso") BigDecimal overrun,
        @JsonProperty("percentual") BigDecimal percentage,
        @JsonProperty("limitePercentual") BigDecimal limitPercentage,
        @JsonProperty("cenarioMaximo") BigDecimal maxScenario,
        @JsonProperty("percentualCenarioMaximo") BigDecimal maxScenarioPercentage,
        @JsonProperty("acimaDoLimite") Boolean aboveLimit,
        @JsonProperty("provisorio") Boolean provisional,
        @JsonProperty("alvo") String target) {
}
