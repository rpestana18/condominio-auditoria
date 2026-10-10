package br.com.condominioauditoria.api.dto.response.budget;

import java.math.BigDecimal;
import java.util.UUID;

/** A line of the column. {@code percentageText}: the "%" printed in the budget, as read. */
public record PrintedColumnLineResponse(
        UUID lineId,
        String code,
        String account,
        String description,
        BigDecimal amount,
        String percentageText) {
}
