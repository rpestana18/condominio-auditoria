package br.com.condominioauditoria.rag.parser.budget;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/**
 * Budget amounts. Unlike the cash flow, the budget has amounts with and without a thousands separator in the same
 * document ("1.234,56" and "1585,14"). Always BigDecimal with 2 decimals, never double.
 */
public final class BudgetAmount {

    /** With thousands ("1.234,56", with every group of 3) or without ("1585,14"); always 2 decimals. */
    private static final Pattern AMOUNT = Pattern.compile("^-?(\\d{1,3}(\\.\\d{3})+|\\d+),\\d{2}$");

    private BudgetAmount() {
    }

    public static boolean isAmount(String text) {
        return AMOUNT.matcher(text.trim()).matches();
    }

    public static BigDecimal fromText(String text) {
        String clean = text.trim();
        if (!isAmount(clean)) {
            throw new IllegalArgumentException("Valor monetário inválido na PO: " + text);
        }
        return new BigDecimal(clean.replace(".", "").replace(",", ".")).setScale(2, RoundingMode.UNNECESSARY);
    }
}
