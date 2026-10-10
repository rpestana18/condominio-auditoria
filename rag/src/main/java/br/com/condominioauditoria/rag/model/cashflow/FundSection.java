package br.com.condominioauditoria.rag.model.cashflow;

import java.math.BigDecimal;
import java.util.List;

/** A fund's block in the cash flow: opening balance, entries and the report's own TOTAIS line. */
public record FundSection(
        String fund,
        BigDecimal openingBalance,
        List<LedgerEntry> entries,
        BigDecimal reportedCreditTotal,
        BigDecimal reportedDebitTotal) {
}
