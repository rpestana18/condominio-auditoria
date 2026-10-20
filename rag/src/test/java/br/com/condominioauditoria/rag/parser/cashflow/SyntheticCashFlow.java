package br.com.condominioauditoria.rag.parser.cashflow;

import br.com.condominioauditoria.rag.model.document.ReadDocument;
import br.com.condominioauditoria.rag.model.document.ReadDocument.Page;
import br.com.condominioauditoria.rag.model.document.ReadDocument.Word;
import java.util.ArrayList;
import java.util.List;

/**
 * Hand-built "FLUXO DE CAIXA" page in the Protest layout, with the column positions of the pilot (no real data). Text
 * is placed word by word from a starting x; amounts are right-aligned on the column end.
 */
public final class SyntheticCashFlow {

    /** Starts of the text columns and ends of the amount columns, as in the pilot header. */
    public static final double DATE = 42.75;
    public static final double ACCOUNT = 98.25;
    public static final double CODE = 207.75;
    public static final double MEMO = 256.5;
    public static final double CREDIT = 422.75;
    public static final double DEBIT = 495.91;
    public static final double BALANCE = 569.67;
    private static final double FUND_NAME = 30;
    private static final double CHAR = 4.5;

    private final List<Word> words = new ArrayList<>();

    public SyntheticCashFlow() {
        text(200, 40, "FLUXO DE CAIXA");
        text(200, 55, "EMPREENDIMENTO: 9999 - CONDOMINIO TESTE");
        text(200, 70, "PERÍODO DE 01/04/2026 À 30/04/2026");
    }

    /** Fund name, column header and SALDO ANTERIOR line. */
    public SyntheticCashFlow fund(double top, String name, String openingBalance) {
        text(FUND_NAME, top, name);
        double header = top + 15;
        text(DATE, header, "DATA");
        text(ACCOUNT, header, "CONTA");
        text(CODE, header, "CÓDIGO");
        text(MEMO, header, "HISTÓRICO");
        right(CREDIT, header, "CRÉDITO");
        right(DEBIT, header, "DÉBITO");
        right(BALANCE + 0.58, header, "SALDO");
        text(MEMO, header + 20, "SALDO ANTERIOR");
        right(BALANCE, header + 20, openingBalance);
        return this;
    }

    /** Entry line: date, account, document, memo and the three amounts, all at the same height. */
    public SyntheticCashFlow entry(double top, String date, String account, String document, String memo,
            String credit, String debit, String balance) {
        text(DATE, top, date);
        account(top, account);
        if (document != null) {
            text(CODE, top, document);
        }
        memo(top, memo);
        right(CREDIT, top, credit);
        right(DEBIT, top, debit);
        right(BALANCE, top, balance);
        return this;
    }

    /** One more line of the memo column (memo text wraps to several lines). */
    public SyntheticCashFlow memo(double top, String text) {
        if (text != null) {
            text(MEMO, top, text);
        }
        return this;
    }

    /** One more line of the ledger account column. */
    public SyntheticCashFlow account(double top, String text) {
        if (text != null) {
            text(ACCOUNT, top, text);
        }
        return this;
    }

    public SyntheticCashFlow totals(double top, String credits, String debits) {
        text(MEMO, top, "TOTAIS");
        right(CREDIT, top, credits);
        right(DEBIT, top, debits);
        return this;
    }

    /** Title of the Posição Financeira table. */
    public SyntheticCashFlow financialPosition(double top) {
        text(200, top, "POSIÇÃO FINANCEIRA");
        return this;
    }

    /** A Posição Financeira line: fund (or TOTAL), saldo anterior, créditos, débitos, saldo atual. */
    public SyntheticCashFlow position(double top, String fund, String opening, String credits, String debits,
            String closing) {
        text(FUND_NAME, top, fund);
        right(300, top, opening);
        right(380, top, credits);
        right(460, top, debits);
        right(540, top, closing);
        return this;
    }

    public ReadDocument document() {
        return new ReadDocument("2", "teste", new ReadDocument.FileInfo("fluxo.pdf", "0".repeat(64), 1), "pdf",
                List.of(new Page(1, 595.32, 841.92, "text", List.copyOf(words))), List.of(), List.of());
    }

    private void text(double x0, double top, String text) {
        double x = x0;
        for (String word : text.split(" ")) {
            double x1 = x + word.length() * CHAR;
            words.add(new Word(word, x, x1, top, top + 7.5));
            x = x1 + CHAR;
        }
    }

    private void right(double x1, double top, String text) {
        words.add(new Word(text, x1 - text.length() * CHAR, x1, top, top + 7.5));
    }
}
