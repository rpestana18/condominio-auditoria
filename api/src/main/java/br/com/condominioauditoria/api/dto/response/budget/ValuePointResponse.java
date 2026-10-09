package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.MonthStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/** A month of a chart series, with the value and the evidence target. */
public record ValuePointResponse(
        @JsonProperty("mes") String month,
        @JsonProperty("situacao") MonthStatus status,
        @JsonProperty("valor") BigDecimal amount,
        @JsonProperty("alvo") String target) {
}
