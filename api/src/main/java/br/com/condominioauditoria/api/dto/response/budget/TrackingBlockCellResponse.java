package br.com.condominioauditoria.api.dto.response.budget;

import java.math.BigDecimal;

/** Amount of a separate block in a month (null without numbers). */
public record TrackingBlockCellResponse(
        String month,
        BigDecimal amount,
        Integer entries) {
}
