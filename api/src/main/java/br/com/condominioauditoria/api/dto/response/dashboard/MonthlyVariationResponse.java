package br.com.condominioauditoria.api.dto.response.dashboard;

import java.math.BigDecimal;

/** Difference to the compared month (RF-12.4). {@code percentage} is null when the compared amount is zero. */
public record MonthlyVariationResponse(
        BigDecimal amount,
        BigDecimal percentage) {
}
