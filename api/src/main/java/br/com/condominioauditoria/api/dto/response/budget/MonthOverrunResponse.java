package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/** Overrun of a month in the fiscal year comparison, in R$ and as a % of planned. */
public record MonthOverrunResponse(
        @JsonProperty("mes") String month,
        @JsonProperty("valor") BigDecimal amount,
        @JsonProperty("percentual") BigDecimal percentage) {
}
