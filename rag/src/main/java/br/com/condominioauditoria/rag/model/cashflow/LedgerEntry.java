package br.com.condominioauditoria.rag.model.cashflow;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A cash flow line, as it is in the management company's report, with its origin (page and position) and the fields
 * enriched from the memo.
 */
public record LedgerEntry(
        int page,
        int sequence,
        LocalDate date,
        String accountCode,
        String accountName,
        String document,
        String memo,
        BigDecimal credit,
        BigDecimal debit,
        BigDecimal balance,
        Enrichment enrichment) {

    /**
     * Information deduced from the memo. Kept apart to make clear what is original and what is inferred.
     * {@code condoFeeReceipt}: "RECIBOS ACUMULADOS" credit of the Protest layout (ADR 0004, Decision 7).
     */
    public record Enrichment(
            String invoiceNumber,
            String supplier,
            String paymentMethod,
            boolean interFundTransfer,
            boolean condoFeeReceipt) {
    }
}
