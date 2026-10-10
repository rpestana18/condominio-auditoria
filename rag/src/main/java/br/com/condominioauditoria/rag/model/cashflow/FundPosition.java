package br.com.condominioauditoria.rag.model.cashflow;

import java.math.BigDecimal;

/** A line of the "Posição Financeira" table at the end of the report. */
public record FundPosition(
        String fund,
        BigDecimal openingBalance,
        BigDecimal credits,
        BigDecimal debits,
        BigDecimal closingBalance) {
}
