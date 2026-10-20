package br.com.condominioauditoria.api.dto.response.budget;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Partial month of the balance bridge (RF-13.10), shown apart from the total: the cash flow has entries only up to
 * {@code partialUntil}. {@code variation} = closing − opening balance printed in that cash flow; {@code inflows} and
 * {@code outflows}: credits and debits of its entries up to that date (Q50).
 */
public record BridgePartialMonthResponse(
        String month,
        LocalDate partialUntil,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        BigDecimal variation,
        BigDecimal inflows,
        BigDecimal outflows) {
}
