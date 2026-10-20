package br.com.condominioauditoria.api.dto.response.budget;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Apportionment fund in the APPORTIONMENT_FUNDS item of the bridge of all funds (Q49): credits ({@code inflows}) and
 * debits ({@code outflows}) of the period without the transfers between funds; {@code amount} = inflows − outflows.
 */
public record BridgeFundResponse(
        UUID fundId,
        String name,
        BigDecimal inflows,
        BigDecimal outflows,
        BigDecimal amount) {
}
