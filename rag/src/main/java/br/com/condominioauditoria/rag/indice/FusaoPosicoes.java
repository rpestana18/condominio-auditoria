package br.com.condominioauditoria.rag.indice;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fusão de posições (Reciprocal Rank Fusion): cada lista dá ao trecho 1 / (k + posição), posição a partir de 1; a
 * pontuação final é a soma. Não usa a nota de cada busca (escalas diferentes), só a posição.
 *
 * Determinística: empate na soma desempata pela melhor posição em qualquer lista e, por fim, pelo id do trecho.
 */
public final class FusaoPosicoes {

    /** Valor usual da literatura e o fixado na ADR 0003. */
    public static final int K = 60;

    private FusaoPosicoes() {
    }

    public record Pontuado(UUID id, double pontuacao) {
    }

    public static List<Pontuado> fundir(List<List<UUID>> listas, int k, int limite) {
        Map<UUID, double[]> acumulado = new LinkedHashMap<>(); // [soma, melhor posição]
        for (List<UUID> lista : listas) {
            for (int i = 0; i < lista.size(); i++) {
                int posicao = i + 1;
                double[] a = acumulado.computeIfAbsent(lista.get(i), id -> new double[] {0, Integer.MAX_VALUE});
                a[0] += 1.0 / (k + posicao);
                a[1] = Math.min(a[1], posicao);
            }
        }
        List<Map.Entry<UUID, double[]>> ordenados = new ArrayList<>(acumulado.entrySet());
        ordenados.sort(Comparator.<Map.Entry<UUID, double[]>>comparingDouble(e -> -e.getValue()[0])
                .thenComparingDouble(e -> e.getValue()[1])
                .thenComparing(e -> e.getKey().toString()));
        return ordenados.stream().limit(limite).map(e -> new Pontuado(e.getKey(), e.getValue()[0])).toList();
    }
}
