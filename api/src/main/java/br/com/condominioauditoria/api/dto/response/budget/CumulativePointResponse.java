package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.MonthStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/** Chart 3: planned and actual summed up to the month (only months with a cash flow enter the sum). */
public record CumulativePointResponse(
        @JsonProperty("mes") String month,
        @JsonProperty("situacao") MonthStatus status,
        @JsonProperty("previstoAcumulado") BigDecimal cumulativePlanned,
        @JsonProperty("realizadoAcumulado") BigDecimal cumulativeActual,
        @JsonProperty("alvo") String target) {
}
