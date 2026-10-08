package br.com.condominioauditoria.api.orcamento;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Formata dinheiro como na tela e nos documentos: 1.234,56 (sempre 2 casas, sem ponto flutuante). */
public final class DinheiroBr {

    private DinheiroBr() {
    }

    public static String formatar(BigDecimal valor) {
        BigDecimal v = valor.setScale(2, RoundingMode.HALF_UP);
        String sinal = v.signum() < 0 ? "-" : "";
        String digitos = v.abs().toPlainString();
        int ponto = digitos.indexOf('.');
        String inteiro = digitos.substring(0, ponto);
        StringBuilder agrupado = new StringBuilder();
        for (int i = 0; i < inteiro.length(); i++) {
            if (i > 0 && (inteiro.length() - i) % 3 == 0) {
                agrupado.append('.');
            }
            agrupado.append(inteiro.charAt(i));
        }
        return sinal + agrupado + "," + digitos.substring(ponto + 1);
    }
}
