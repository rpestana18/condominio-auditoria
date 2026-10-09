package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.MonthStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/** Chart 1: actual ÷ monthly planned, with the 100% reference. */
public record ExecutionPointResponse(
        @JsonProperty("mes") String month,
        @JsonProperty("situacao") MonthStatus status,
        @JsonProperty("previsto") BigDecimal planned,
        @JsonProperty("realizado") BigDecimal actual,
        @JsonProperty("execucao") BigDecimal execution,
        @JsonProperty("alvo") String target) {
}
