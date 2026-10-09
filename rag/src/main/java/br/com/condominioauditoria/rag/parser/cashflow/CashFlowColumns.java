package br.com.condominioauditoria.rag.parser.cashflow;

import br.com.condominioauditoria.rag.model.document.ReadDocument.Word;
import br.com.condominioauditoria.rag.parser.TextLine;
import java.util.Optional;

/**
 * Column positions, taken from the header "DATA CONTA CONTÁBIL CÓDIGO HISTÓRICO CRÉDITO DÉBITO SALDO". Each fund has
 * its own header, and the positions change a little from one to another. Text columns start at the title's x0; amount
 * columns are right-aligned, by x1.
 */
public record CashFlowColumns(double account, double code, double memo, double creditRight, double debitRight,
        double balanceRight) {

    /** Maximum distance between the end of a number and the end of the column title. */
    private static final double AMOUNT_TOLERANCE = 15;

    public static Optional<CashFlowColumns> fromHeader(TextLine line) {
        if (!line.contains("DATA") || !line.contains("HISTÓRICO") || !line.contains("CRÉDITO")) {
            return Optional.empty();
        }
        // In some funds "CONTA CONTÁBIL" breaks into two lines, outside the header line.
        // The account column starts right after the date.
        double account = line.contains("CONTA") ? word(line, "CONTA").x0() : word(line, "DATA").x1() + 5;
        return Optional.of(new CashFlowColumns(
                account,
                word(line, "CÓDIGO").x0(),
                word(line, "HISTÓRICO").x0(),
                word(line, "CRÉDITO").x1(),
                word(line, "DÉBITO").x1(),
                word(line, "SALDO").x1()));
    }

    private static Word word(TextLine line, String text) {
        return line.words().stream().filter(p -> p.text().equals(text)).findFirst()
                .orElseThrow(() -> new CashFlowParser.CashFlowReadException(
                        "Cabeçalho sem a coluna " + text + " na pág. " + line.page()));
    }

    public enum Amount { CREDIT, DEBIT, BALANCE }

    /** Which amount column the number falls into, or empty if it is not aligned with any. */
    public Optional<Amount> amountColumn(Word p) {
        double c = Math.abs(p.x1() - creditRight);
        double d = Math.abs(p.x1() - debitRight);
        double s = Math.abs(p.x1() - balanceRight);
        double smallest = Math.min(c, Math.min(d, s));
        if (smallest > AMOUNT_TOLERANCE) {
            return Optional.empty();
        }
        return Optional.of(smallest == c ? Amount.CREDIT : smallest == d ? Amount.DEBIT : Amount.BALANCE);
    }

    public enum Text { ACCOUNT, CODE, MEMO }

    public Optional<Text> textColumn(Word p) {
        double x = p.x0() + 2;
        if (x >= memo) {
            return Optional.of(Text.MEMO);
        }
        if (x >= code) {
            return Optional.of(Text.CODE);
        }
        if (x >= account) {
            return Optional.of(Text.ACCOUNT);
        }
        return Optional.empty();
    }
}
