package br.com.condominioauditoria.api.auditoria;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Regra "fundo de reserva da PO acima do teto" (Conv. 20.1; RF-03.1.3; matriz de regras do RF-03.1). Declarativa e
 * versionada: mudar a regra é subir {@link #VERSAO}. O teto vem do parâmetro {@link #PARAMETRO} do condomínio, com
 * vigência. Comparação exata, sem arredondar: há achado se {@code reserva × 100 > previsto do mês × teto}.
 */
public final class RegraTetoFundoReserva {

    public static final String CODIGO = "FUNDO_RESERVA_ACIMA_TETO";
    public static final String VERSAO = "1";
    public static final String PARAMETRO = "TETO_FUNDO_RESERVA_PERCENTUAL";
    public static final Severidade SEVERIDADE = Severidade.ATENCAO;

    private static final BigDecimal CEM = new BigDecimal("100");

    /** @param percentual reserva sobre o previsto do mês, com 10 casas (exibido com 1 casa) */
    public record Avaliacao(boolean acimaDoTeto, BigDecimal percentual, BigDecimal teto) {

        public String percentualExibido() {
            return percentual.setScale(1, RoundingMode.HALF_UP).toPlainString().replace('.', ',') + "%";
        }

        public String tetoExibido() {
            return teto.stripTrailingZeros().toPlainString().replace('.', ',') + "%";
        }
    }

    private RegraTetoFundoReserva() {
    }

    /** Vazio quando o previsto do mês não é positivo (não há base para o percentual). */
    public static Optional<Avaliacao> avaliar(BigDecimal reservaMensal, BigDecimal previstoMes, BigDecimal tetoPercentual) {
        if (previstoMes == null || previstoMes.signum() <= 0) {
            return Optional.empty();
        }
        boolean acima = reservaMensal.multiply(CEM).compareTo(previstoMes.multiply(tetoPercentual)) > 0;
        BigDecimal percentual = reservaMensal.multiply(CEM).divide(previstoMes, 10, RoundingMode.HALF_UP);
        return Optional.of(new Avaliacao(acima, percentual, tetoPercentual));
    }
}
