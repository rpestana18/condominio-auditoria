package br.com.condominioauditoria.backend.orcamento;

import br.com.condominioauditoria.backend.auditoria.ParametroRegra;
import br.com.condominioauditoria.backend.auditoria.ParametroRegraRepository;
import br.com.condominioauditoria.backend.auditoria.RegraTetoFundoReserva;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Aplica a regra do teto do fundo de reserva a uma PO confirmada. A linha do fundo de reserva é a linha do grupo de
 * fundos cujo texto impresso (descrição ou coluna de conta) fala em "reserva"; o percentual é calculado pelos valores
 * (orçado da linha sobre o previsto do mês), não pela coluna "%", que é texto lido.
 */
@Component
public class ReservaDaPo {

    public sealed interface Resultado {
    }

    public record Avaliada(LinhaPo linha, RegraTetoFundoReserva.Avaliacao avaliacao, ParametroRegra parametro)
            implements Resultado {
    }

    public record NaoAvaliada(String motivo) implements Resultado {
    }

    private final ParametroRegraRepository parametros;

    public ReservaDaPo(ParametroRegraRepository parametros) {
        this.parametros = parametros;
    }

    public Resultado avaliar(PrevisaoOrcamentaria po, EstruturaPo estrutura) {
        List<LinhaPo> reservas = estrutura.fundos().map(EstruturaPo.Grupo::linhas).orElse(List.of()).stream()
                .filter(ReservaDaPo::ehReserva).toList();
        if (reservas.size() != 1) {
            return new NaoAvaliada("Regra do teto do fundo de reserva (Conv. 20.1) não avaliada: "
                    + (reservas.isEmpty() ? "a PO não tem linha de fundo de reserva"
                            : "mais de uma linha de fundo de reserva na PO"));
        }
        Optional<ParametroRegra> teto = parametros.vigente(po.getCondominioId(), RegraTetoFundoReserva.PARAMETRO,
                po.getExercicioInicio().atDay(1));
        if (teto.isEmpty()) {
            return new NaoAvaliada("Regra do teto do fundo de reserva (Conv. 20.1) não avaliada: teto não cadastrado"
                    + " para o condomínio em " + po.getExercicioInicio());
        }
        LinhaPo reserva = reservas.getFirst();
        return RegraTetoFundoReserva.avaliar(reserva.getOrcado(), po.getPrevistoMes(), teto.get().getValor())
                .<Resultado>map(a -> new Avaliada(reserva, a, teto.get()))
                .orElseGet(() -> new NaoAvaliada("Regra do teto do fundo de reserva (Conv. 20.1) não avaliada:"
                        + " previsto do mês sem valor"));
    }

    private static boolean ehReserva(LinhaPo l) {
        return EstruturaPo.normalizar(l.getDescricao()).contains("reserva")
                || (l.getContaTexto() != null && EstruturaPo.normalizar(l.getContaTexto()).contains("reserva"));
    }
}
