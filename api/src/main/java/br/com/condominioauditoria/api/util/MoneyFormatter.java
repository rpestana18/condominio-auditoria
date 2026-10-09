package br.com.condominioauditoria.api.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Formats money as on the screen and in the documents: 1.234,56 (always 2 decimals, no floating point). */
public final class MoneyFormatter {

    private MoneyFormatter() {
    }

    public static String format(BigDecimal amount) {
        BigDecimal v = amount.setScale(2, RoundingMode.HALF_UP);
        String sign = v.signum() < 0 ? "-" : "";
        String digits = v.abs().toPlainString();
        int point = digits.indexOf('.');
        String integer = digits.substring(0, point);
        StringBuilder grouped = new StringBuilder();
        for (int i = 0; i < integer.length(); i++) {
            if (i > 0 && (integer.length() - i) % 3 == 0) {
                grouped.append('.');
            }
            grouped.append(integer.charAt(i));
        }
        return sign + grouped + "," + digits.substring(point + 1);
    }
}
