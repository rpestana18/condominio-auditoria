package br.com.condominioauditoria.api.orcamento;

import java.time.YearMonth;
import java.util.Collection;
import java.util.Optional;

/**
 * Meses em que uma PO vale (RF-03.1.3: só uma PO por mês de cada condomínio). Confirmada: o exercício inteiro.
 * Substituída: do início do exercício até o mês anterior ao da nova versão. Função pura.
 *
 * <p>Prorrogação (RF-11.3; ADR 0005, Decisão 4): a PO confirmada vale também do mês seguinte ao fim do exercício até o
 * mês marcado pelo Admin, mas só quando nenhuma PO confirmada cobre o mês pelo exercício. Os meses prorrogados não
 * fazem parte do exercício: {@link #de} não os inclui.
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

    /** Meses prorrogados da PO (do mês seguinte ao fim do exercício até a prorrogação), ou vazio. */
    public static Optional<VigenciaPo> prorrogacao(PrevisaoOrcamentaria p) {
        if (p.getEstado() != EstadoPrevisao.CONFIRMADA || p.getExercicioFim() == null || p.getProrrogadaAte() == null
                || !p.getProrrogadaAte().isAfter(p.getExercicioFim())) {
            return Optional.empty();
        }
        return Optional.of(new VigenciaPo(p, p.getExercicioFim().plusMonths(1), p.getProrrogadaAte()));
    }

    /** PO do mês e se ela vale por prorrogação ("PO prorrogada"). */
    public record PoDoMes(PrevisaoOrcamentaria previsao, boolean prorrogada) {
    }

    /**
     * PO do mês (ADR 0005, Decisão 4): (1) a PO confirmada cujo exercício cobre o mês; senão (2) a PO cuja prorrogação
     * cobre o mês; senão vazio ("sem PO aprovada para este mês").
     */
    public static Optional<PoDoMes> doMes(Collection<PrevisaoOrcamentaria> previsoes, YearMonth mes) {
        Optional<PrevisaoOrcamentaria> pelo = previsoes.stream().map(VigenciaPo::de).flatMap(Optional::stream)
                .filter(v -> v.cobre(mes)).map(VigenciaPo::previsao).findFirst();
        if (pelo.isPresent()) {
            return Optional.of(new PoDoMes(pelo.get(), false));
        }
        return previsoes.stream().map(VigenciaPo::prorrogacao).flatMap(Optional::stream).filter(v -> v.cobre(mes))
                .map(v -> new PoDoMes(v.previsao(), true)).findFirst();
    }

    public boolean cobre(YearMonth mes) {
        return !mes.isBefore(inicio) && !mes.isAfter(fim);
    }

    public boolean sobrepoe(YearMonth outroInicio, YearMonth outroFim) {
        return !outroFim.isBefore(inicio) && !outroInicio.isAfter(fim);
    }

    /** A PO que vale no mês, pelo exercício ou por prorrogação, ou vazio ("sem PO aprovada para este mês"). */
    public static Optional<PrevisaoOrcamentaria> vigenteNoMes(Collection<PrevisaoOrcamentaria> previsoes, YearMonth mes) {
        return doMes(previsoes, mes).map(PoDoMes::previsao);
    }

    public String periodo() {
        return inicio + " a " + fim;
    }
}
