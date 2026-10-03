package br.com.condominioauditoria.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/** Valores monetários: sempre BigDecimal com 2 casas. Nunca double. */
public final class Dinheiro {

    private static final Pattern FORMATO_BR = Pattern.compile("^-?\\d{1,3}(\\.\\d{3})*,\\d{2}$");

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private Dinheiro() {
    }

    /** Converte "1.234,56", "-941,54" ou "-" (vazio no relatório) em BigDecimal. */
    public static BigDecimal deTextoBr(String texto) {
        String limpo = texto.trim();
        if (limpo.equals("-")) {
            return ZERO;
        }
        if (!ehValorBr(limpo)) {
            throw new IllegalArgumentException("Valor monetário inválido: " + texto);
        }
        return new BigDecimal(limpo.replace(".", "").replace(",", ".")).setScale(2, RoundingMode.UNNECESSARY);
    }

    public static boolean ehValorBr(String texto) {
        return FORMATO_BR.matcher(texto.trim()).matches();
    }

    /** 1234.5 vira "1.234,50", para mensagens legíveis. */
    public static String formatarBr(BigDecimal valor) {
        var formato = java.text.NumberFormat.getNumberInstance(java.util.Locale.of("pt", "BR"));
        formato.setMinimumFractionDigits(2);
        formato.setMaximumFractionDigits(2);
        return formato.format(valor);
    }

    /** "-" também é aceito como célula de valor (significa zero). */
    public static boolean ehCelulaDeValor(String texto) {
        return texto.equals("-") || ehValorBr(texto);
    }
}
