package br.com.condominioauditoria.api.model.usage;

import br.com.condominioauditoria.api.model.enums.UsageFunction;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sum of the usage of a feature function, in one month (month = "YYYY-MM") or in the whole period (null month). Only
 * counts: the cost is calculated apart (tokens × catalog price, in BigDecimal).
 */
public record UsageTotal(String month, String feature, UsageFunction function, long count, long inputTokens,
        long outputTokens, long files, long pages) {

    private static final Comparator<UsageTotal> ORDER = Comparator.comparing(UsageTotal::feature)
            .thenComparing(UsageTotal::function);

    /** Joins the months into one total per feature and function, ordered by feature and function (deterministic). */
    public static List<UsageTotal> sumByFunction(List<UsageTotal> byMonth) {
        Map<String, UsageTotal> totals = new LinkedHashMap<>();
        for (UsageTotal t : byMonth) {
            totals.merge(t.feature() + "|" + t.function().code(),
                    new UsageTotal(null, t.feature(), t.function(), t.count(), t.inputTokens(), t.outputTokens(),
                            t.files(), t.pages()),
                    (a, b) -> new UsageTotal(null, a.feature(), a.function(), a.count() + b.count(),
                            a.inputTokens() + b.inputTokens(), a.outputTokens() + b.outputTokens(),
                            a.files() + b.files(), a.pages() + b.pages()));
        }
        return totals.values().stream().sorted(ORDER).toList();
    }
}
