package br.com.condominioauditoria.api.dto.response.budget;

import java.math.BigDecimal;
import java.util.UUID;

/** Line that went over planned in the month (the 20% rule). */
public record OverrunLineResponse(
        UUID lineId,
        String code,
        String description,
        BigDecimal overrun) {
}
