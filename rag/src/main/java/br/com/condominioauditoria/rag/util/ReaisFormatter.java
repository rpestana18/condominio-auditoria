package br.com.condominioauditoria.rag.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money of the "Nos dados gravados" block: the query contract sends exact decimal text ("1234.56"); here it becomes
 * {@link BigDecimal} and goes out in the Brazilian format ("R$ 1.234,56"), with the same formatter as the rest of the
 * rag ({@link BrazilianMoney#format}). It never goes through floating point, and the cents come out as they came (no
 * silent rounding).
 */
public final class ReaisFormatter {

    private ReaisFormatter() {
    }

    /** Decimal text of the query contract as BigDecimal with 2 places. Empty = zero. */
    public static BigDecimal parse(String decimal) {
        if (decimal == null || decimal.isBlank()) {
            return BigDecimal.ZERO.setScale(2);
        }
        return new BigDecimal(decimal.trim()).setScale(2, RoundingMode.UNNECESSARY);
    }

    /** "1234.56" becomes "R$ 1.234,56"; negative becomes "-R$ 1.234,56". */
    public static String format(String decimal) {
        return format(parse(decimal));
    }

    public static String format(BigDecimal value) {
        BigDecimal twoPlaces = value.setScale(2, RoundingMode.UNNECESSARY);
        boolean negative = twoPlaces.signum() < 0;
        return (negative ? "-R$ " : "R$ ") + BrazilianMoney.format(twoPlaces.abs());
    }
}
