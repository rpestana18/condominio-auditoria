package br.com.condominioauditoria.api.modulo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Período em que um módulo esteve ligado num condomínio (RF-10.6), calculado da trilha de ativação. inicio nulo só
 * quando o módulo nasce ligado por padrão (antes de qualquer evento); fim nulo = ainda ligado. Nenhum valor de
 * cobrança é calculado nesta fase.
 */
public record PeriodoAtivo(String modulo, Instant inicio, Instant fim, String ligadoPor, String motivoLigar,
        String desligadoPor, String motivoDesligar) {

    /**
     * Percorre os eventos em ordem cronológica: ligar abre um período, desligar fecha o aberto. O mesmo conjunto de
     * eventos sempre dá os mesmos períodos.
     *
     * @param ligadoPorPadrao estado antes do primeiro evento (padrão do catálogo)
     */
    public static List<PeriodoAtivo> calcular(String modulo, boolean ligadoPorPadrao, List<EventoModulo> eventos) {
        List<PeriodoAtivo> periodos = new ArrayList<>();
        boolean ligado = ligadoPorPadrao;
        Instant inicio = null;
        String ligadoPor = null;
        String motivoLigar = null;
        for (EventoModulo e : eventos) {
            if (e.isLigadoDepois() == ligado) {
                continue; // a trilha não grava evento sem mudança; se aparecer, não abre nem fecha nada
            }
            if (e.isLigadoDepois()) {
                inicio = e.getQuando();
                ligadoPor = e.getUsuario();
                motivoLigar = e.getMotivo();
            } else {
                periodos.add(new PeriodoAtivo(modulo, inicio, e.getQuando(), ligadoPor, motivoLigar, e.getUsuario(),
                        e.getMotivo()));
                inicio = null;
                ligadoPor = null;
                motivoLigar = null;
            }
            ligado = e.isLigadoDepois();
        }
        if (ligado) {
            periodos.add(new PeriodoAtivo(modulo, inicio, null, ligadoPor, motivoLigar, null, null));
        }
        return List.copyOf(periodos);
    }

    /** Toca o intervalo [de, ate)? Período aberto vale até agora. */
    public boolean tocaIntervalo(Instant de, Instant ate) {
        boolean comecouAntesDoFim = inicio == null || inicio.isBefore(ate);
        boolean terminouDepoisDoInicio = fim == null || !fim.isBefore(de);
        return comecouAntesDoFim && terminouDepoisDoInicio;
    }
}
