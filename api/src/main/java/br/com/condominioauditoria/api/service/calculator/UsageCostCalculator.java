package br.com.condominioauditoria.api.service.calculator;

import br.com.condominioauditoria.api.model.enums.UsageFunction;
import br.com.condominioauditoria.api.model.usage.UsageTotal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Estimated usage cost, in US$ (RF-09.7; ADR 0003, Decision 4): tokens × price of the rag's catalog per million tokens,
 * per provider and model. The cost is not saved per record: it is calculated here, in the period report, in exact
 * BigDecimal, and rounded to 2 decimals (half up) only on each total shown (month and function, function in the period
 * and grand total). So adding the rounded totals may differ by 1 cent from the grand total, which is the rounding of
 * the exact sum. Same records and same prices = same result.
 *
 * Tokens of a model without a price in the catalog leave the total of that group (and the grand total) without a value,
 * and the model is listed in modelsWithoutPrice: nothing is estimated with a made-up price.
 */
public final class UsageCostCalculator {

    static final int SCALE = 2;
    static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    private UsageCostCalculator() {
    }

    /** Price in US$ per million input and output tokens. */
    public record ModelPrice(BigDecimal inputPerMillionUsd, BigDecimal outputPerMillionUsd) {

        public ModelPrice {
            Objects.requireNonNull(inputPerMillionUsd);
            Objects.requireNonNull(outputPerMillionUsd);
        }

        public static String key(String provider, String model) {
            return (provider == null ? "" : provider) + "/" + (model == null ? "" : model);
        }
    }

    /** Tokens added up for a month, feature, function, provider and model (output of the repository query). */
    public record ModelTokens(String month, String feature, UsageFunction function, String provider, String model,
            long inputTokens, long outputTokens) {
    }

    /**
     * Result: cost per month+feature+function ({@link #monthKey}) and per feature+function in the period ({@link
     * #functionKey}), and the total. Null value = there are tokens of a model without price in that group. A group
     * without tokens does not appear.
     */
    public record PeriodCost(Map<String, BigDecimal> byMonth, Map<String, BigDecimal> byFunction, BigDecimal total,
            Set<String> modelsWithoutPrice) {

        public static String monthKey(String month, String feature, UsageFunction function) {
            return month + "|" + feature + "|" + function.code();
        }

        public static String functionKey(String feature, UsageFunction function) {
            return feature + "|" + function.code();
        }

        public BigDecimal ofMonth(UsageTotal t) {
            return byMonth.get(monthKey(t.month(), t.feature(), t.function()));
        }

        public BigDecimal ofFunction(UsageTotal t) {
            return byFunction.get(functionKey(t.feature(), t.function()));
        }
    }

    public static PeriodCost calculate(List<ModelTokens> rows, Map<String, ModelPrice> prices) {
        Map<String, BigDecimal> exactByMonth = new LinkedHashMap<>();
        Map<String, BigDecimal> exactByFunction = new LinkedHashMap<>();
        Set<String> withoutPriceByMonth = new TreeSet<>();
        Set<String> withoutPriceByFunction = new TreeSet<>();
        Set<String> modelsWithoutPrice = new TreeSet<>();
        BigDecimal total = BigDecimal.ZERO;
        boolean totalWithoutPrice = false;

        for (ModelTokens l : rows) {
            if (l.inputTokens() == 0 && l.outputTokens() == 0) {
                continue;
            }
            String month = PeriodCost.monthKey(l.month(), l.feature(), l.function());
            String function = PeriodCost.functionKey(l.feature(), l.function());
            exactByMonth.putIfAbsent(month, BigDecimal.ZERO);
            exactByFunction.putIfAbsent(function, BigDecimal.ZERO);
            ModelPrice price = prices.get(ModelPrice.key(l.provider(), l.model()));
            if (price == null) {
                modelsWithoutPrice.add(ModelPrice.key(l.provider(), l.model()));
                withoutPriceByMonth.add(month);
                withoutPriceByFunction.add(function);
                totalWithoutPrice = true;
                continue;
            }
            BigDecimal cost = exact(l.inputTokens(), l.outputTokens(), price);
            exactByMonth.merge(month, cost, BigDecimal::add);
            exactByFunction.merge(function, cost, BigDecimal::add);
            total = total.add(cost);
        }
        return new PeriodCost(round(exactByMonth, withoutPriceByMonth), round(exactByFunction, withoutPriceByFunction),
                totalWithoutPrice ? null : round(total), modelsWithoutPrice);
    }

    /** Exact cost, no rounding: (input × input price + output × output price) / 1,000,000. */
    static BigDecimal exact(long inputTokens, long outputTokens, ModelPrice price) {
        return BigDecimal.valueOf(inputTokens).multiply(price.inputPerMillionUsd())
                .add(BigDecimal.valueOf(outputTokens).multiply(price.outputPerMillionUsd()))
                .divide(MILLION); // dividing by 10^6 is always exact
    }

    static BigDecimal round(BigDecimal value) {
        return value.setScale(SCALE, ROUNDING);
    }

    private static Map<String, BigDecimal> round(Map<String, BigDecimal> exact, Set<String> withoutPrice) {
        Map<String, BigDecimal> rounded = new LinkedHashMap<>();
        exact.forEach((key, value) -> rounded.put(key, withoutPrice.contains(key) ? null : round(value)));
        return rounded;
    }
}
