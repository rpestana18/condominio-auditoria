package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/** Variation against the previous fiscal year. {@code newInFiscalYear}: zero base and a non-zero current value. */
public record VariationResponse(
        @JsonProperty("valor") BigDecimal amount,
        @JsonProperty("percentual") BigDecimal percentage,
        @JsonProperty("novaNoExercicio") boolean newInFiscalYear) {
}
