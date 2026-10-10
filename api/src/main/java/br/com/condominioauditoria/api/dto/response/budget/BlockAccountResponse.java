package br.com.condominioauditoria.api.dto.response.budget;

import java.math.BigDecimal;

/** Account inside a separate block, with its amount and number of entries. */
public record BlockAccountResponse(
        String account,
        String name,
        String detail,
        BigDecimal amount,
        int entries) {
}
