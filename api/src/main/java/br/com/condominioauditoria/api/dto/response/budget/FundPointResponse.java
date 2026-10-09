package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.MonthStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/** A month of chart 6: planned and collected of a fund. */
public record FundPointResponse(
        @JsonProperty("mes") String month,
        @JsonProperty("situacao") MonthStatus status,
        @JsonProperty("previsto") BigDecimal planned,
        @JsonProperty("arrecadado") BigDecimal collected,
        @JsonProperty("alvo") String target) {
}
