package br.com.condominioauditoria.api.orcamento;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * O de-para que vale nos números (RF-03.1.4 e RF-03.1.5): só o CONFIRMADO. Sugerido e recusado não ligam a conta a
 * destino nenhum, e os lançamentos dela vão para "sem linha da PO" (premissa 3: nunca somada em silêncio a outra
 * linha). Função pura.
 */
public final class DeparaEfetivo {

    private DeparaEfetivo() {
    }

    /** Conta do fluxo → destino confirmado. */
    public static Map<String, Destino> confirmados(Collection<DeparaConta> deparas) {
        Map<String, Destino> m = new TreeMap<>();
        deparas.stream().filter(d -> d.getEstado() == EstadoDepara.CONFIRMADO)
                .forEach(d -> m.put(d.getContaCodigo(), d.destino()));
        return m;
    }
}
