package br.com.condominioauditoria.rag.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rank fusion (Reciprocal Rank Fusion): each list gives the chunk 1 / (k + rank), rank from 1; the final score is the
 * sum. It does not use each search's own score (different scales), only the rank.
 *
 * Deterministic: a tie in the sum is broken by the best rank in any list and, last, by the chunk id.
 */
public final class RankFusion {

    /** Usual value in the literature and the one fixed in ADR 0003. */
    public static final int K = 60;

    private RankFusion() {
    }

    public record Scored(UUID id, double score) {
    }

    public static List<Scored> fuse(List<List<UUID>> lists, int k, int limit) {
        Map<UUID, double[]> accumulated = new LinkedHashMap<>(); // [sum, best rank]
        for (List<UUID> list : lists) {
            for (int i = 0; i < list.size(); i++) {
                int rank = i + 1;
                double[] a = accumulated.computeIfAbsent(list.get(i), id -> new double[] {0, Integer.MAX_VALUE});
                a[0] += 1.0 / (k + rank);
                a[1] = Math.min(a[1], rank);
            }
        }
        List<Map.Entry<UUID, double[]>> sortedItems = new ArrayList<>(accumulated.entrySet());
        sortedItems.sort(Comparator.<Map.Entry<UUID, double[]>>comparingDouble(e -> -e.getValue()[0])
                .thenComparingDouble(e -> e.getValue()[1])
                .thenComparing(e -> e.getKey().toString()));
        return sortedItems.stream().limit(limit).map(e -> new Scored(e.getKey(), e.getValue()[0])).toList();
    }
}
