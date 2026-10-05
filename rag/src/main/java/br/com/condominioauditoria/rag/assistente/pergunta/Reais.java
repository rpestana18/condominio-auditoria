package br.com.condominioauditoria.rag.assistente.pergunta;

import br.com.condominioauditoria.rag.dominio.Dinheiro;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Dinheiro do bloco "Nos dados gravados": o contrato de consulta manda texto decimal exato ("1234.56"); aqui vira
 * {@link BigDecimal} e sai no padrão brasileiro ("R$ 1.234,56"), com o mesmo formatador do resto do rag
 * ({@link Dinheiro#formatarBr}). Nunca passa por ponto flutuante, e os centavos saem como vieram (nada de
 * arredondamento silencioso).
 */
public final class Reais {

    private Reais() {
    }

    /** Texto decimal do contrato de consulta em BigDecimal com 2 casas. Vazio = zero. */
    public static BigDecimal valor(String decimal) {
        if (decimal == null || decimal.isBlank()) {
            return BigDecimal.ZERO.setScale(2);
        }
        return new BigDecimal(decimal.trim()).setScale(2, RoundingMode.UNNECESSARY);
    }

    /** "1234.56" vira "R$ 1.234,56"; negativo vira "-R$ 1.234,56". */
    public static String formatar(String decimal) {
        return formatar(valor(decimal));
    }

    public static String formatar(BigDecimal valor) {
        BigDecimal duasCasas = valor.setScale(2, RoundingMode.UNNECESSARY);
        boolean negativo = duasCasas.signum() < 0;
        return (negativo ? "-R$ " : "R$ ") + Dinheiro.formatarBr(duasCasas.abs());
    }
}
