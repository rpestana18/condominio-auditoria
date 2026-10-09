package br.com.condominioauditoria.rag.parser.cashflow;

import br.com.condominioauditoria.rag.model.cashflow.LedgerEntry.Enrichment;
import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Takes from the memo what can be safely deduced: invoice, supplier, payment method, transfer and condo fee receipt.
 */
public final class EntryEnricher {

    private static final Pattern INVOICE = Pattern.compile("\\b(?:NF|NOTA FISCAL):?\\s*(\\d{1,10})");
    private static final Pattern SUPPLIER = Pattern.compile("\\bDE:\\s*(.+?)\\s*(?:\\(\\d+\\))?$");
    private static final Pattern PAYMENT_METHOD = Pattern.compile("MERCADO PAGO|CART[ÃA]O DE CR[ÉE]DITO|PICPAY|PAGSEGURO");

    /** Memo of the condo fee credits in the Protest layout: the receipts paid on the day, without a ledger account. */
    static final String ACCUMULATED_RECEIPTS = "RECIBOS ACUMULADOS";

    private EntryEnricher() {
    }

    public static Enrichment enrich(String accountName, String memo, BigDecimal credit, BigDecimal debit) {
        String invoice = firstGroup(INVOICE, memo);
        String supplier = firstGroup(SUPPLIER, memo);
        Matcher means = PAYMENT_METHOD.matcher(memo);
        boolean transfer = accountName.contains("TRANSFERENCIA CONTABIL") || accountName.contains("AJUSTE CONTABIL")
                || memo.startsWith("TRANSFERENCIA DE ");
        return new Enrichment(invoice, supplier, means.find() ? means.group() : null, transfer,
                condoFeeReceipt(memo, credit, debit));
    }

    /**
     * "RECIBOS ACUMULADOS" credit (ADR 0004, Decision 7). Only the credit column counts: the amount can be negative
     * (receipt reversal, as in FUNDO INADIMPLENTES), and a line with a debit is never a condo fee receipt.
     */
    public static boolean condoFeeReceipt(String memo, BigDecimal credit, BigDecimal debit) {
        return ACCUMULATED_RECEIPTS.equals(memo.trim()) && credit.signum() != 0 && debit.signum() == 0;
    }

    private static String firstGroup(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : null;
    }
}
