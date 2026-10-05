package br.com.condominioauditoria.backend.orcamento;

import java.time.YearMonth;
import java.util.Collection;
import java.util.Optional;

/**
 * Meses em que uma PO vale (RF-03.1.3: só uma PO por mês de cada condomínio). Confirmada: o exercício inteiro.
 * Substituída: do início do exercício até o mês anterior ao da nova versão. Função pura.
 */
public record VigenciaPo(PrevisaoOrcamentaria previsao, YearMonth inicio, YearMonth fim) {

    public static Optional<VigenciaPo> de(PrevisaoOrcamentaria p) {
        if (!p.getEstado().travada() || p.getExercicioInicio() == null) {
            return Optional.empty();
        }
        YearMonth fim = p.getExercicioFim();
        if (p.getEstado() == EstadoPrevisao.SUBSTITUIDA && p.getSubstituidaDesde() != null) {
            YearMonth antes = p.getSubstituidaDesde().minusMonths(1);
            fim = antes.isBefore(fim) ? antes : fim;
        }
        if (fim.isBefore(p.getExercicioInicio())) {
            return Optional.empty();
        }
        return Optional.of(new VigenciaPo(p, p.getExercicioInicio(), fim));
    }

    public boolean cobre(YearMonth mes) {
        return !mes.isBefore(inicio) && !mes.isAfter(fim);
    }

    public boolean sobrepoe(YearMonth outroInicio, YearMonth outroFim) {
        return !outroFim.isBefore(inicio) && !outroInicio.isAfter(fim);
    }

    /** A PO que vale no mês, ou vazio ("sem PO aprovada para este mês"). */
    public static Optional<PrevisaoOrcamentaria> vigenteNoMes(Collection<PrevisaoOrcamentaria> previsoes, YearMonth mes) {
        return previsoes.stream().map(VigenciaPo::de).flatMap(Optional::stream).filter(v -> v.cobre(mes))
                .map(VigenciaPo::previsao).findFirst();
    }

    public String periodo() {
        return inicio + " a " + fim;
    }
}
