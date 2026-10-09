package br.com.condominioauditoria.rag.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/** Money amounts: always BigDecimal with 2 decimals. Never double. */
public final class BrazilianMoney {

    private static final Pattern BR_FORMAT = Pattern.compile("^-?\\d{1,3}(\\.\\d{3})*,\\d{2}$");

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private BrazilianMoney() {
    }

    /** Converts "1.234,56", "-941,54" or "-" (empty in the report) to BigDecimal. */
    public static BigDecimal fromText(String text) {
        String clean = text.trim();
        if (clean.equals("-")) {
            return ZERO;
        }
        if (!isAmount(clean)) {
            throw new IllegalArgumentException("Valor monetário inválido: " + text);
        }
        return new BigDecimal(clean.replace(".", "").replace(",", ".")).setScale(2, RoundingMode.UNNECESSARY);
    }

    public static boolean isAmount(String text) {
        return BR_FORMAT.matcher(text.trim()).matches();
    }

    /** 1234.5 becomes "1.234,50", for readable messages. */
    public static String format(BigDecimal amount) {
        var format = java.text.NumberFormat.getNumberInstance(java.util.Locale.of("pt", "BR"));
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(amount);
    }

    /** "-" is also accepted as an amount cell (it means zero). */
    public static boolean isAmountCell(String text) {
        return text.equals("-") || isAmount(text);
    }
}
