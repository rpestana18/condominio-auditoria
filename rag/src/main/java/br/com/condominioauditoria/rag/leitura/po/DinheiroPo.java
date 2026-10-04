package br.com.condominioauditoria.rag.leitura.po;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/**
 * Valores da PO. Diferente do fluxo, a PO traz valores com e sem separador de milhar no mesmo documento
 * ("1.234,56" e "1585,14"). Sempre BigDecimal com 2 casas, nunca double.
 */
final class DinheiroPo {

    /** Com milhar ("1.234,56", com todos os grupos de 3) ou sem milhar ("1585,14"); sempre 2 casas. */
    private static final Pattern VALOR = Pattern.compile("^-?(\\d{1,3}(\\.\\d{3})+|\\d+),\\d{2}$");

    private DinheiroPo() {
    }

    static boolean ehValor(String texto) {
        return VALOR.matcher(texto.trim()).matches();
    }

    static BigDecimal deTexto(String texto) {
        String limpo = texto.trim();
        if (!ehValor(limpo)) {
            throw new IllegalArgumentException("Valor monetário inválido na PO: " + texto);
        }
        return new BigDecimal(limpo.replace(".", "").replace(",", ".")).setScale(2, RoundingMode.UNNECESSARY);
    }
}
