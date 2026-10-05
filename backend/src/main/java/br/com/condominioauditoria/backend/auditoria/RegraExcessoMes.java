package br.com.condominioauditoria.backend.auditoria;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Regra dos 20% (Conv. 16.2; RF-03.1.11; Q23 e Q26), por mês, no fundo Condomínio. Declarativa e versionada: mudar a
 * regra é subir {@link #VERSAO}. O limite vem do parâmetro {@link #PARAMETRO} do condomínio, com vigência.
 *
 * <p>Excesso = soma das diferenças positivas, linha a linha, das linhas de despesa (Q23); "a realocar" e "sem linha da
 * PO" ficam fora (Q26). Comparação exata, sem arredondar: há achado se {@code excesso × 100 > previsto do mês ×
 * limite}. Assim 90.324,02 sobre 451.620,10 (exatamente 20%) não gera achado, e 90.324,03 gera.
 */
public final class RegraExcessoMes {

    public static final String CODIGO = "EXCESSO_MES_ACIMA_LIMITE";
    public static final String VERSAO = "1";
    public static final String PARAMETRO = "LIMITE_EXCESSO_MES_PERCENTUAL";
    public static final Severidade SEVERIDADE = Severidade.CRITICO;

    private static final BigDecimal CEM = new BigDecimal("100");

    /**
     * @param percentual excesso sobre o previsto do mês, com 10 casas (exibido com 1 casa)
     * @param limite limite em reais (previsto do mês × limite %), arredondado para centavos só para exibir
     */
    public record Avaliacao(boolean acimaDoLimite, BigDecimal percentual, BigDecimal limite, BigDecimal limitePercentual) {
    }

    private RegraExcessoMes() {
    }

    /** Vazio quando o previsto do mês não é positivo (não há base para o percentual). */
    public static Optional<Avaliacao> avaliar(BigDecimal excesso, BigDecimal previstoMes, BigDecimal limitePercentual) {
        if (previstoMes == null || previstoMes.signum() <= 0) {
            return Optional.empty();
        }
        boolean acima = excesso.multiply(CEM).compareTo(previstoMes.multiply(limitePercentual)) > 0;
        BigDecimal percentual = excesso.multiply(CEM).divide(previstoMes, 10, RoundingMode.HALF_UP);
        BigDecimal limite = previstoMes.multiply(limitePercentual).divide(CEM, 2, RoundingMode.HALF_UP);
        return Optional.of(new Avaliacao(acima, percentual, limite, limitePercentual));
    }
}
