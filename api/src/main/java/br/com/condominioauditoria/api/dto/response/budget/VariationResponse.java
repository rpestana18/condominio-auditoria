package br.com.condominioauditoria.api.dto.response.budget;

import java.math.BigDecimal;

/** Variation against the previous fiscal year. {@code newInFiscalYear}: zero base and a non-zero current value. */
public record VariationResponse(
        BigDecimal amount,
        BigDecimal percentage,
        boolean newInFiscalYear) {
}
