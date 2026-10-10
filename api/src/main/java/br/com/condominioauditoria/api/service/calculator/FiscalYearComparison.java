package br.com.condominioauditoria.api.service.calculator;

import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualGroupResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualLineResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualResponse;
import br.com.condominioauditoria.api.dto.response.budget.ComparedFiscalYearResponse;
import br.com.condominioauditoria.api.dto.response.budget.ComparedGroupResponse;
import br.com.condominioauditoria.api.dto.response.budget.ComparedItemResponse;
import br.com.condominioauditoria.api.dto.response.budget.ComparedValueResponse;
import br.com.condominioauditoria.api.dto.response.budget.FiscalYearComparisonResponse;
import br.com.condominioauditoria.api.dto.response.budget.FiscalYearMonthResponse;
import br.com.condominioauditoria.api.dto.response.budget.FiscalYearSummaryResponse;
import br.com.condominioauditoria.api.dto.response.budget.FundResultResponse;
import br.com.condominioauditoria.api.dto.response.budget.MonthOverrunResponse;
import br.com.condominioauditoria.api.dto.response.budget.UnmatchedLineResponse;
import br.com.condominioauditoria.api.dto.response.budget.UsedLineResponse;
import br.com.condominioauditoria.api.dto.response.budget.VariationResponse;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.enums.BudgetLineType;
import br.com.condominioauditoria.api.model.enums.FiscalYearType;
import br.com.condominioauditoria.api.model.enums.FundComparisonStatus;
import br.com.condominioauditoria.api.model.enums.MonthStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Fiscal year comparison (RF-11.6; ADR 0005, Decision 2): builds the three views (summary, per group and per budget
 * item) from the {@link BudgetVsActualCalculator} result of each fiscal year, without database or clock access. No
 * number of a fiscal year changes: the actual comes from the same calculation as the budget vs. actual screen.
 *
 * <p>Rules:
 * <ul>
 * <li>fiscal years from the most recent to the oldest; the variation of each is against the next one in the list
 * (the previous);
 * <li>variation in R$ = current − previous; in % only with a non-zero base (10 decimals, shown with 1 decimal, half
 * up); zero base with a current value = "nova no exercício";
 * <li>month planned = sum of the lines (Q29), without the funds; in the printed column, the "Orçado anterior" column;
 * <li>groups matched by code (1.1 to 1.9), unmatched; lines matched by the confirmed budget item (RF-11.7); a line
 * without a confirmed budget item goes to "sem correspondência" and is never added to another;
 * <li>funds (1.9) compare the collection (RF-03.1.9).
 * </ul>
 */
public final class FiscalYearComparison {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final Locale PT_BR = Locale.of("pt", "BR");

    private FiscalYearComparison() {
    }

    /** Confirmed budget item of a line. */
    public record LineItem(UUID id, String name, String group) {
    }

    /**
     * A fiscal year to compare. {@code lines}: the budget's (in the printed column, those of the budget that printed
     * it, with the "Orçado anterior" column value). {@code cumulative}: the fiscal year cumulative calculation (null in
     * the printed column). {@code period}: the results summed in the comparison (the cumulative, or the "mesmos meses"
     * months); empty = no actual. {@code periodMonths}: the months of those results (YYYY-MM).
     */
    public record Input(String id, FiscalYearType type, String label, UUID budgetId, Integer version, YearMonth start,
            YearMonth end, List<BudgetLine> lines, Map<UUID, UUID> fundByLine, Map<UUID, LineItem> items,
            BudgetVsActualResponse cumulative, List<BudgetVsActualResponse> period, List<String> periodMonths,
            Integer openFindings) {

        public boolean column() {
            return type == FiscalYearType.PRINTED_COLUMN;
        }
    }

    /** Null {@code fundId} = all; the operating fund (Condomínio) = without the 1.9 funds; another = only its lines. */
    public record Filter(UUID fundId, UUID operatingFundId, boolean sameMonths, String comparing) {
    }

    /** Numbers of a line in a fiscal year (period planned and actual null without actual). */
    private record LineValue(BudgetLine line, String group, BigDecimal monthlyPlanned, BigDecimal planned,
            BigDecimal actual, String target) {
    }

    /** A fiscal year already assessed, line by line, within the filter scope. */
    private record Assessed(Input input, List<BudgetStructure.Group> groups, Map<String, List<LineValue>> byGroup,
            boolean withActual) {
    }

    public static FiscalYearComparisonResponse compare(List<Input> inputs, Filter filter) {
        List<Assessed> assessed = inputs.stream().map(e -> assess(e, filter)).toList();
        List<ComparedFiscalYearResponse> fiscalYears = inputs.stream().map(e -> new ComparedFiscalYearResponse(e.id(),
                e.type(),
                e.label(), e.budgetId(), e.version(), e.start().toString(), e.end().toString(),
                e.column() ? null : evidencePeriod(e), List.copyOf(e.periodMonths()))).toList();
        return new FiscalYearComparisonResponse(fiscalYears, filter.fundId(), filter.sameMonths(), filter.comparing(),
                summary(assessed, filter), groups(assessed), items(assessed), unmatched(assessed),
                warnings(inputs));
    }

    /**
     * Months compared in "mesmos meses": the months of the year (by number) with a "com fluxo" situation in every
     * fiscal year with a budget (the printed column has no actual and is left out). Extended months do not count.
     */
    public static Set<Month> sameMonths(Collection<BudgetVsActualResponse> cumulatives) {
        Set<Month> common = null;
        for (BudgetVsActualResponse a : cumulatives) {
            Set<Month> ofFiscalYear = a == null ? Set.of() : a.months().stream()
                    .filter(m -> !m.extended() && m.status() == MonthStatus.WITH_CASH_FLOW)
                    .map(m -> YearMonth.parse(m.month()).getMonth()).collect(Collectors.toCollection(TreeSet::new));
            if (common == null) {
                common = new TreeSet<>(ofFiscalYear);
            } else {
                common.retainAll(ofFiscalYear);
            }
        }
        return common == null ? Set.of() : Set.copyOf(common);
    }

    /** "comparando: setembro", "comparando: agosto e setembro" or "comparando: nenhum mês com fluxo em todos". */
    public static String comparing(Set<Month> months) {
        if (months.isEmpty()) {
            return "comparando: nenhum mês com fluxo em todos os exercícios";
        }
        List<String> names = new TreeSet<>(months).stream().map(m -> m.getDisplayName(TextStyle.FULL, PT_BR)).toList();
        String text = names.size() == 1 ? names.getFirst()
                : String.join(", ", names.subList(0, names.size() - 1)) + " e " + names.getLast();
        return "comparando: " + text;
    }

    /** Variation of {@code current} against {@code previous}; null if either is missing. */
    public static VariationResponse variation(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null) {
            return null;
        }
        BigDecimal amount = current.subtract(previous).setScale(2, RoundingMode.HALF_UP);
        if (previous.signum() == 0) {
            return new VariationResponse(amount, null, current.signum() != 0);
        }
        BigDecimal pct = amount.multiply(BigDecimal.valueOf(100)).divide(previous.abs(), 10, RoundingMode.HALF_UP)
                .setScale(1, RoundingMode.HALF_UP);
        return new VariationResponse(amount, pct, false);
    }

    private static Assessed assess(Input e, Filter f) {
        BudgetStructure structure = BudgetStructure.of(e.lines());
        Map<UUID, List<BudgetVsActualLineResponse>> ofPeriod = new HashMap<>();
        Map<UUID, List<FundResultResponse>> periodFunds = new HashMap<>();
        for (BudgetVsActualResponse r : e.period()) {
            for (BudgetVsActualGroupResponse g : nullIfNone(r.groups())) {
                for (BudgetVsActualLineResponse l : g.lines()) {
                    ofPeriod.computeIfAbsent(l.lineId(), k -> new ArrayList<>()).add(l);
                }
            }
            for (FundResultResponse fr : nullIfNone(r.funds())) {
                if (fr.lineId() != null) {
                    periodFunds.computeIfAbsent(fr.lineId(), k -> new ArrayList<>()).add(fr);
                }
            }
        }
        boolean withActual = !e.column() && !e.period().isEmpty();
        List<BudgetStructure.Group> groups = new ArrayList<>();
        Map<String, List<LineValue>> byGroup = new LinkedHashMap<>();
        for (BudgetStructure.Group g : structure.groups()) {
            if (!groupInFilter(g, f)) {
                continue;
            }
            List<LineValue> values = new ArrayList<>();
            for (BudgetLine l : g.lines()) {
                if (g.funds() && f.fundId() != null && !f.fundId().equals(e.fundByLine().get(l.getId()))) {
                    continue;
                }
                BigDecimal planned = null;
                BigDecimal actual = null;
                String target = null;
                if (withActual && g.funds()) {
                    List<FundResultResponse> frs = periodFunds.getOrDefault(l.getId(), List.of());
                    if (!frs.isEmpty() && frs.stream().allMatch(fr -> fr.status() == FundComparisonStatus.COMPARED)) {
                        planned = sum(frs.stream().map(FundResultResponse::planned).toList());
                        actual = sum(frs.stream().map(FundResultResponse::collected).toList());
                        target = BudgetVsActualCalculator.fundTarget(frs.getFirst().fundId());
                    }
                } else if (withActual) {
                    List<BudgetVsActualLineResponse> lineItems = ofPeriod.getOrDefault(l.getId(), List.of());
                    if (!lineItems.isEmpty()) {
                        planned = sum(lineItems.stream().map(BudgetVsActualLineResponse::planned).toList());
                        actual = sum(lineItems.stream().map(BudgetVsActualLineResponse::actual).toList());
                    }
                    target = BudgetVsActualCalculator.lineTarget(l.getId());
                }
                values.add(new LineValue(l, g.line().getEffectiveCode(), lineValue(e, l), planned, actual,
                        target));
            }
            if (g.funds() && values.isEmpty()) {
                continue;
            }
            groups.add(g);
            byGroup.put(g.line().getEffectiveCode(), values);
        }
        return new Assessed(e, groups, byGroup, withActual);
    }

    private static boolean groupInFilter(BudgetStructure.Group g, Filter f) {
        if (f.fundId() == null) {
            return true;
        }
        return f.fundId().equals(f.operatingFundId()) != g.funds();
    }

    private static boolean otherFundFilter(Filter f) {
        return f.fundId() != null && !f.fundId().equals(f.operatingFundId());
    }

    private static List<FiscalYearSummaryResponse> summary(List<Assessed> assessed, Filter f) {
        List<FiscalYearSummaryResponse> list = new ArrayList<>();
        BigDecimal[] monthlyPlanned = new BigDecimal[assessed.size()];
        BigDecimal[] actual = new BigDecimal[assessed.size()];
        for (int i = 0; i < assessed.size(); i++) {
            Assessed a = assessed.get(i);
            List<LineValue> lines = a.byGroup().entrySet().stream()
                    .filter(x -> otherFundFilter(f) || !fundGroup(a, x.getKey()))
                    .flatMap(x -> x.getValue().stream()).toList();
            monthlyPlanned[i] = sum(lines.stream().map(LineValue::monthlyPlanned).toList());
            if (a.withActual()) {
                if (otherFundFilter(f)) {
                    actual[i] = sumOrNull(lines.stream().map(LineValue::actual).toList());
                } else {
                    actual[i] = sum(a.input().period().stream().map(r -> r.totals().actualExpense()).toList());
                }
            }
        }
        for (int i = 0; i < assessed.size(); i++) {
            Assessed a = assessed.get(i);
            Input e = a.input();
            BigDecimal planned = null;
            MonthOverrunResponse largest = null;
            Integer above = null;
            boolean provisional = false;
            if (a.withActual()) {
                if (otherFundFilter(f)) {
                    planned = sumOrNull(a.byGroup().values().stream().flatMap(List::stream)
                            .map(LineValue::planned).toList());
                } else {
                    planned = sum(e.period().stream().map(r -> r.totals().planned()).toList());
                    provisional = e.period().stream().anyMatch(BudgetVsActualResponse::provisional);
                    List<FiscalYearMonthResponse> months = e.cumulative() == null ? List.of() : e.cumulative().months().stream()
                            .filter(m -> !m.extended() && e.periodMonths().contains(m.month())).toList();
                    largest = months.stream().filter(m -> m.overrun() != null)
                            .max(Comparator.comparing(FiscalYearMonthResponse::overrun))
                            .map(m -> new MonthOverrunResponse(m.month(), m.overrun(),
                                    m.overrunPercentage())).orElse(null);
                    above = (int) months.stream().filter(m -> Boolean.TRUE.equals(m.aboveLimit())).count();
                }
            }
            int monthCount = (int) (e.end().getYear() * 12L + e.end().getMonthValue()
                    - (e.start().getYear() * 12L + e.start().getMonthValue()) + 1);
            BigDecimal previousMonth = i + 1 < assessed.size() ? monthlyPlanned[i + 1] : null;
            BigDecimal previousActual = i + 1 < assessed.size() ? actual[i + 1] : null;
            list.add(new FiscalYearSummaryResponse(e.id(), monthlyPlanned[i],
                    monthlyPlanned[i].multiply(BigDecimal.valueOf(monthCount)).setScale(2, RoundingMode.UNNECESSARY),
                    a.withActual() ? e.periodMonths().size() : 0, planned, actual[i],
                    actual[i] == null ? null : BudgetVsActualCalculator.percentage(actual[i], planned), largest,
                    above, e.openFindings(), provisional, variation(monthlyPlanned[i], previousMonth),
                    variation(actual[i], previousActual),
                    a.withActual() ? (otherFundFilter(f) ? BudgetVsActualCalculator.fundTarget(f.fundId())
                            : BudgetVsActualCalculator.TARGET_TOTAL) : null));
        }
        return List.copyOf(list);
    }

    private static boolean fundGroup(Assessed a, String code) {
        return a.groups().stream().anyMatch(g -> g.funds() && g.line().getEffectiveCode().equals(code));
    }

    private static List<ComparedGroupResponse> groups(List<Assessed> assessed) {
        // Order: that of the groups in the most recent fiscal year; those that exist only in the others come after
        Map<String, BudgetStructure.Group> position = new LinkedHashMap<>();
        assessed.forEach(a -> a.groups().forEach(g -> position.putIfAbsent(g.line().getEffectiveCode(), g)));
        List<ComparedGroupResponse> list = new ArrayList<>();
        for (Map.Entry<String, BudgetStructure.Group> x : position.entrySet()) {
            List<ComparedValueResponse> values = values(assessed, a -> {
                List<LineValue> lines = a.byGroup().get(x.getKey());
                if (lines == null) {
                    return null;
                }
                BudgetStructure.Group g = a.groups().stream()
                        .filter(gr -> gr.line().getEffectiveCode().equals(x.getKey())).findFirst().orElseThrow();
                String target = !a.withActual() ? null : g.funds()
                        ? (lines.size() == 1 ? lines.getFirst().target() : null)
                        : BudgetVsActualCalculator.groupTarget(g.line().getId());
                return new Partial(lines, target);
            });
            list.add(new ComparedGroupResponse(x.getKey(), x.getValue().line().getDescription(), x.getValue().funds(),
                    values));
        }
        return List.copyOf(list);
    }

    private static List<ComparedItemResponse> items(List<Assessed> assessed) {
        Map<UUID, LineItem> all = new LinkedHashMap<>();
        for (Assessed a : assessed) {
            a.byGroup().values().stream().flatMap(List::stream)
                    .map(v -> a.input().items().get(v.line().getId())).filter(Objects::nonNull)
                    .forEach(r -> all.putIfAbsent(r.id(), r));
        }
        List<ComparedItemResponse> list = new ArrayList<>();
        for (LineItem r : all.values()) {
            List<ComparedValueResponse> values = values(assessed, a -> {
                List<LineValue> lines = a.byGroup().values().stream().flatMap(List::stream)
                        .filter(v -> r.equals(a.input().items().get(v.line().getId()))).toList();
                return lines.isEmpty() ? null
                        : new Partial(lines, lines.size() == 1 ? lines.getFirst().target() : null);
            });
            list.add(new ComparedItemResponse(r.id(), r.name(), r.group(), values));
        }
        return List.copyOf(list);
    }

    private static List<UnmatchedLineResponse> unmatched(List<Assessed> assessed) {
        List<UnmatchedLineResponse> list = new ArrayList<>();
        for (Assessed a : assessed) {
            a.byGroup().values().stream().flatMap(List::stream)
                    .filter(v -> !a.input().items().containsKey(v.line().getId()))
                    .forEach(v -> list.add(new UnmatchedLineResponse(a.input().id(), v.line().getId(),
                            v.line().getEffectiveCode(), v.line().getAccount(), v.line().getDescription(), v.group(),
                            v.monthlyPlanned(), v.planned(), v.actual(), v.target())));
        }
        return List.copyOf(list);
    }

    /** Lines of a fiscal year in a group or budget item, and the evidence target of the set. */
    private record Partial(List<LineValue> lines, String target) {
    }

    private static List<ComparedValueResponse> values(List<Assessed> assessed, Function<Assessed, Partial> partial) {
        List<Partial> parts = assessed.stream().map(partial).toList();
        List<ComparedValueResponse> list = new ArrayList<>();
        for (int i = 0; i < assessed.size(); i++) {
            Partial p = parts.get(i);
            Assessed a = assessed.get(i);
            BigDecimal monthlyPlanned = p == null ? null : sum(p.lines().stream().map(LineValue::monthlyPlanned).toList());
            BigDecimal planned = p == null || !a.withActual() ? null
                    : sumOrNull(p.lines().stream().map(LineValue::planned).toList());
            BigDecimal actual = p == null || !a.withActual() ? null
                    : sumOrNull(p.lines().stream().map(LineValue::actual).toList());
            VariationResponse plannedVariation = null;
            VariationResponse actualVariation = null;
            if (i + 1 < assessed.size()) {
                Partial prev = parts.get(i + 1);
                Assessed aa = assessed.get(i + 1);
                BigDecimal prevMonthlyPlanned = prev == null ? null
                        : sum(prev.lines().stream().map(LineValue::monthlyPlanned).toList());
                // Line or group that did not exist in the previous one: zero base ("nova no exercício")
                plannedVariation = monthlyPlanned == null ? null : variation(monthlyPlanned,
                        prevMonthlyPlanned == null ? ZERO
                        : prevMonthlyPlanned);
                BigDecimal prevActual = prev == null || !aa.withActual() ? null
                        : sumOrNull(prev.lines().stream().map(LineValue::actual).toList());
                actualVariation = variation(actual, prevActual);
            }
            list.add(new ComparedValueResponse(a.input().id(), monthlyPlanned, planned, actual, plannedVariation,
                    actualVariation,
                    p == null ? null : p.target(), p == null ? List.of() : p.lines().stream()
                            .map(v -> new UsedLineResponse(v.line().getId(), v.line().getEffectiveCode(),
                                    v.line().getDescription(), v.target())).toList()));
        }
        return List.copyOf(list);
    }

    private static List<String> warnings(List<Input> inputs) {
        List<String> warnings = new ArrayList<>();
        for (Input e : inputs) {
            long without = e.lines().stream().filter(l -> l.getType() == BudgetLineType.LINE)
                    .filter(l -> !e.items().containsKey(l.getId())).count();
            if (without > 0) {
                warnings.add(e.label() + ": " + without + (without == 1 ? " linha" : " linhas")
                        + " sem rubrica confirmada (bloco \"sem correspondência\")");
            }
            if (!e.column() && e.period().isEmpty()) {
                warnings.add(e.label() + ": sem fluxo carregado nos meses comparados (só previsto)");
            }
        }
        return List.copyOf(warnings);
    }

    private static String evidencePeriod(Input e) {
        return e.periodMonths().size() == 1 && e.period().size() == 1
                && !e.period().getFirst().period().equalsIgnoreCase("cumulative") ? e.periodMonths().getFirst()
                : "cumulative";
    }

    private static BigDecimal lineValue(Input e, BudgetLine l) {
        BigDecimal v = e.column() ? l.getPreviousBudgeted() : l.getBudgeted();
        return v == null ? ZERO : v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().filter(Objects::nonNull).reduce(ZERO, BigDecimal::add);
    }

    /** Sum; null if any value is null (an unassessed number never becomes zero, RF-03.1.10). */
    private static BigDecimal sumOrNull(List<BigDecimal> values) {
        return values.stream().anyMatch(Objects::isNull) ? null : sum(values);
    }

    private static <T> List<T> nullIfNone(List<T> list) {
        return list == null ? List.of() : list;
    }
}
