package br.com.condominioauditoria.rag.parser.cashflow;

import br.com.condominioauditoria.rag.model.cashflow.LedgerEntry.Enrichment;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Takes from the memo what can be safely deduced: invoice, supplier, payment method, transfer and condo fee receipt.
 */
public final class EntryEnricher {

    private static final Pattern INVOICE = Pattern.compile("\\b(?:NF|NOTA FISCAL):?\\s*(\\d{1,10})");
    private static final Pattern SUPPLIER = Pattern.compile("\\bDE:\\s*(.+?)\\s*(?:\\(\\d+\\))?$");
    private static final Pattern PAYMENT_METHOD = Pattern.compile("MERCADO PAGO|CART[ÃA]O DE CR[ÉE]DITO|PICPAY|PAGSEGURO");
    /**
     * Ledger accounts of a transfer between funds, compared without accents: "TRANSFERENCIA CONTABIL",
     * "TRANSFERÊNCIAS CONTÁBEIS", "TRANSF CONTABEIS", "TRANSF. COTA EXTRA DÉBITO", "TRANSF A DEBITOS OBRAS" and
     * "AJUSTE CONTABIL". "TRANSFERÊNCIA ENTRE CONTAS" alone is not enough: the pilot also uses it for money that comes
     * from an account outside the report ("APORTE DA CONTA EXTERNA ..."); for it, the memo decides.
     */
    private static final Pattern TRANSFER_ACCOUNT = Pattern.compile(
            "\\bTRANSFERENCIAS? CONTAB(?:IL|EIS)\\b|\\bTRANSF\\b|\\bAJUSTES? CONTAB(?:IL|EIS)\\b");
    /**
     * Memo of a transfer between funds, compared without accents: "TRANSFERENCIA CONTABIL DE COTA EXTRA PARA
     * CONDOMINIO", "AJUSTE CONTÁBIL DE CONCESSIONÁRIAS PARA AGUA/ESGOTO ...", "TRANSFERENCIA DE ...". Both sides of the
     * transfer carry the same memo, so both are marked even when one side has a generic account.
     */
    private static final Pattern TRANSFER_MEMO = Pattern.compile(
            "^(?:(?:TRANSFERENCIA|AJUSTE) CONTABIL\\b|TRANSFERENCIA DE )");

    /** Memo of the condo fee credits in the Protest layout: the receipts paid on the day, without a ledger account. */
    static final String ACCUMULATED_RECEIPTS = "RECIBOS ACUMULADOS";

    private EntryEnricher() {
    }

    public static Enrichment enrich(String accountName, String memo, BigDecimal credit, BigDecimal debit) {
        String invoice = firstGroup(INVOICE, memo);
        String supplier = firstGroup(SUPPLIER, memo);
        Matcher means = PAYMENT_METHOD.matcher(memo);
        boolean transfer = interFundTransfer(accountName, memo);
        return new Enrichment(invoice, supplier, means.find() ? means.group() : null, transfer,
                condoFeeReceipt(memo, credit, debit));
    }

    /** Transfer between funds of the same report, by the ledger account or by the memo (see the two patterns). */
    public static boolean interFundTransfer(String accountName, String memo) {
        return TRANSFER_ACCOUNT.matcher(withoutAccents(accountName)).find()
                || TRANSFER_MEMO.matcher(withoutAccents(memo).trim()).find();
    }

    private static String withoutAccents(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
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
