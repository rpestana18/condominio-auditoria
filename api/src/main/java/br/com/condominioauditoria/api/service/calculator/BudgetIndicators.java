package br.com.condominioauditoria.api.service.calculator;

import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualGroupResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualLineResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualResponse;
import br.com.condominioauditoria.api.dto.response.budget.ComparedGroupResponse;
import br.com.condominioauditoria.api.dto.response.budget.ComparedValueResponse;
import br.com.condominioauditoria.api.dto.response.budget.ComparisonFiscalYearResponse;
import br.com.condominioauditoria.api.dto.response.budget.ComparisonGroupResponse;
import br.com.condominioauditoria.api.dto.response.budget.CumulativePointResponse;
import br.com.condominioauditoria.api.dto.response.budget.ExecutionPointResponse;
import br.com.condominioauditoria.api.dto.response.budget.FiscalYearComparisonResponse;
import br.com.condominioauditoria.api.dto.response.budget.FiscalYearMonthResponse;
import br.com.condominioauditoria.api.dto.response.budget.FundPointResponse;
import br.com.condominioauditoria.api.dto.response.budget.FundResultResponse;
import br.com.condominioauditoria.api.dto.response.budget.FundSeriesResponse;
import br.com.condominioauditoria.api.dto.response.budget.GroupSeriesResponse;
import br.com.condominioauditoria.api.dto.response.budget.IndicatorComparisonResponse;
import br.com.condominioauditoria.api.dto.response.budget.IndicatorsResponse;
import br.com.condominioauditoria.api.dto.response.budget.LargestDifferencesResponse;
import br.com.condominioauditoria.api.dto.response.budget.LineDifferenceResponse;
import br.com.condominioauditoria.api.dto.response.budget.Rule20PointResponse;
import br.com.condominioauditoria.api.dto.response.budget.Rule20Response;
import br.com.condominioauditoria.api.dto.response.budget.UsedCashFlowResponse;
import br.com.condominioauditoria.api.dto.response.budget.ValuePointResponse;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.enums.BudgetVsActualStatus;
import br.com.condominioauditoria.api.model.enums.FiscalYearType;
import br.com.condominioauditoria.api.model.enums.FundComparisonStatus;
import br.com.condominioauditoria.api.model.enums.MonthStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Series of the 7 charts of the "Indicadores" screen (RF-11.10 to RF-11.13; ADR 0005, Decisions 5 and 6). Pure
 * function: receives the cumulative calculation and the one of each fiscal year month, the same as the budget vs.
 * actual screen, and only rearranges the numbers (premise 4 of RF-11: the charts calculate nothing). A month without a
 * cash flow, or with two cash flows, comes with null numbers and the situation, never with zero. Each point carries
 * the month and the evidence target (RF-11.12).
 *
 * <p>Charts 1 to 5 are about the Condomínio fund; chart 6, the funds linked to the 1.9 lines; chart 7, the fiscal
 * year comparison. Fund filter: the Condomínio fund hides chart 6; another fund hides 1 to 5 and leaves only itself in
 * chart 6.
 */
public final class BudgetIndicators {

    /** Furthest above and below planned in chart 5. */
    static final int TOP = 10;

    private BudgetIndicators() {
    }

    /** A fiscal year month and its calculation (null without a loaded cash flow or with two cash flows). */
    public record CalculatedMonth(YearMonth month, MonthStatus status, BudgetVsActualResponse result) {
    }

    public record Input(Budget budget, String label, BudgetVsActualResponse cumulative,
            List<CalculatedMonth> months, UUID fundId, UUID operatingFundId, FiscalYearComparisonResponse comparison,
            String withoutComparison) {
    }

    public static IndicatorsResponse build(Input e) {
        boolean otherFund = e.fundId() != null && !e.fundId().equals(e.operatingFundId());
        boolean operatingOnly = e.fundId() != null && e.fundId().equals(e.operatingFundId());
        List<String> warnings = new ArrayList<>();
        long withoutCashFlow = e.months().stream().filter(m -> m.status() != MonthStatus.WITH_CASH_FLOW).count();
        if (withoutCashFlow > 0) {
            warnings.add(withoutCashFlow + (withoutCashFlow == 1 ? " mês" : " meses") + " do exercício sem números (sem fluxo"
                    + " carregado ou com dois fluxos)");
        }
        if (e.comparison() == null && e.withoutComparison() != null) {
            warnings.add(e.withoutComparison());
        }
        BigDecimal limit = e.months().stream().map(CalculatedMonth::result).filter(Objects::nonNull)
                .map(BudgetVsActualResponse::rule20).filter(Objects::nonNull).map(Rule20Response::limitPercentage).findFirst()
                .orElse(null);
        return new IndicatorsResponse(e.budget().getId(), e.label(), e.budget().getFiscalYearStart().toString(),
                e.budget().getFiscalYearEnd().toString(), e.fundId(), dataAsOf(e), limit,
                otherFund ? null : execution(e), otherFund ? null : rule20(e), otherFund ? null : cumulative(e),
                otherFund ? null : byGroup(e), otherFund ? null : largestDifferences(e.cumulative()),
                operatingOnly ? null : funds(e), comparison(e.comparison()), List.copyOf(warnings));
    }

    private static List<ExecutionPointResponse> execution(Input e) {
        return e.months().stream().map(m -> {
            BudgetVsActualResponse r = m.result();
            if (r == null) {
                return new ExecutionPointResponse(m.month().toString(), m.status(), null, null, null, null);
            }
            return new ExecutionPointResponse(m.month().toString(), m.status(), r.totals().planned(),
                    r.totals().actualExpense(), r.totals().execution(), BudgetVsActualCalculator.TARGET_TOTAL);
        }).toList();
    }

    private static List<Rule20PointResponse> rule20(Input e) {
        return e.months().stream().map(m -> {
            Rule20Response r = m.result() == null ? null : m.result().rule20();
            if (r == null) {
                return new Rule20PointResponse(m.month().toString(), m.status(), null, null, null, null, null, null,
                        null,
                        null);
            }
            return new Rule20PointResponse(m.month().toString(), m.status(), r.overrun(), r.percentage(),
                    r.limitPercentage(), r.maxScenario(), r.maxScenarioPercentage(), r.aboveLimit(),
                    r.provisional(), BudgetVsActualCalculator.TARGET_TOTAL);
        }).toList();
    }

    private static List<CumulativePointResponse> cumulative(Input e) {
        List<CumulativePointResponse> list = new ArrayList<>();
        BigDecimal planned = null;
        BigDecimal actual = null;
        for (CalculatedMonth m : e.months()) {
            BudgetVsActualResponse r = m.result();
            if (r == null) {
                list.add(new CumulativePointResponse(m.month().toString(), m.status(), null, null, null));
                continue;
            }
            planned = (planned == null ? BigDecimal.ZERO.setScale(2) : planned).add(r.totals().planned());
            actual = (actual == null ? BigDecimal.ZERO.setScale(2) : actual)
                    .add(r.totals().actualExpense());
            list.add(new CumulativePointResponse(m.month().toString(), m.status(), planned, actual,
                    BudgetVsActualCalculator.TARGET_TOTAL));
        }
        return List.copyOf(list);
    }

    private static List<GroupSeriesResponse> byGroup(Input e) {
        // Budget groups in document order (1.1 to 1.8); the funds go to chart 6
        Map<UUID, GroupName> groups = new LinkedHashMap<>();
        for (CalculatedMonth m : e.months()) {
            if (m.result() == null) {
                continue;
            }
            for (BudgetVsActualGroupResponse g : m.result().groups()) {
                groups.computeIfAbsent(g.lineId(), k -> new GroupName(g.code(), g.description()));
            }
        }
        if (groups.isEmpty() && e.cumulative() != null && e.cumulative().groups() != null) {
            e.cumulative().groups().forEach(g -> groups.put(g.lineId(),
                    new GroupName(g.code(), g.description())));
        }
        List<GroupSeriesResponse> list = new ArrayList<>();
        for (Map.Entry<UUID, GroupName> x : groups.entrySet()) {
            List<ValuePointResponse> points = e.months().stream().map(m -> {
                BudgetVsActualGroupResponse g = m.result() == null ? null : m.result().groups().stream()
                        .filter(gr -> gr.lineId().equals(x.getKey())).findFirst().orElse(null);
                return g == null ? new ValuePointResponse(m.month().toString(), m.status(), null, null)
                        : new ValuePointResponse(m.month().toString(), m.status(), g.actual(),
                                BudgetVsActualCalculator.groupTarget(g.lineId()));
            }).toList();
            list.add(new GroupSeriesResponse(x.getValue().code(), x.getValue().description(), points));
        }
        return List.copyOf(list);
    }

    private record GroupName(String code, String description) {
    }

    /**
     * Chart 5, from the cumulative: the 10 lines furthest above (difference &gt; 0) and the 10 furthest below (&lt; 0).
     */
    public static LargestDifferencesResponse largestDifferences(BudgetVsActualResponse cumulative) {
        if (cumulative == null || cumulative.status() != BudgetVsActualStatus.CALCULATED) {
            return new LargestDifferencesResponse(List.of(), List.of());
        }
        List<LineDifferenceResponse> all = cumulative.groups().stream().flatMap(g -> g.lines().stream())
                .map(BudgetIndicators::difference).toList();
        List<LineDifferenceResponse> above = all.stream().filter(d -> d.difference().signum() > 0)
                .sorted(Comparator.comparing(LineDifferenceResponse::difference).reversed().thenComparing(LineDifferenceResponse::code))
                .limit(TOP).toList();
        List<LineDifferenceResponse> below = all.stream().filter(d -> d.difference().signum() < 0)
                .sorted(Comparator.comparing(LineDifferenceResponse::difference).thenComparing(LineDifferenceResponse::code))
                .limit(TOP).toList();
        return new LargestDifferencesResponse(above, below);
    }

    private static LineDifferenceResponse difference(BudgetVsActualLineResponse l) {
        return new LineDifferenceResponse(l.lineId(), l.code(), l.description(), l.planned(), l.actual(),
                l.difference(),
                BudgetVsActualCalculator.lineTarget(l.lineId()));
    }

    private static List<FundSeriesResponse> funds(Input e) {
        Map<UUID, FundResultResponse> ofFiscalYear = new LinkedHashMap<>();
        List<BudgetVsActualResponse> results = new ArrayList<>();
        if (e.cumulative() != null && e.cumulative().funds() != null) {
            results.add(e.cumulative());
        }
        e.months().stream().map(CalculatedMonth::result).filter(Objects::nonNull).forEach(results::add);
        for (BudgetVsActualResponse r : results) {
            for (FundResultResponse f : r.funds()) {
                if (f.fundId() != null && f.lineId() != null
                        && (e.fundId() == null || e.fundId().equals(f.fundId()))) {
                    ofFiscalYear.putIfAbsent(f.fundId(), f);
                }
            }
        }
        List<FundSeriesResponse> list = new ArrayList<>();
        for (FundResultResponse f : ofFiscalYear.values()) {
            List<FundPointResponse> points = e.months().stream().map(m -> {
                FundResultResponse ofMonth = m.result() == null ? null : m.result().funds().stream()
                        .filter(x -> f.fundId().equals(x.fundId()) && x.status() == FundComparisonStatus.COMPARED)
                        .findFirst().orElse(null);
                return ofMonth == null ? new FundPointResponse(m.month().toString(), m.status(), null, null, null)
                        : new FundPointResponse(m.month().toString(), m.status(), ofMonth.planned(),
                                ofMonth.collected(),
                                BudgetVsActualCalculator.fundTarget(f.fundId()));
            }).toList();
            list.add(new FundSeriesResponse(f.fundId(), f.fund(), f.lineCode(), points));
        }
        return List.copyOf(list);
    }

    private static IndicatorComparisonResponse comparison(FiscalYearComparisonResponse c) {
        if (c == null) {
            return null;
        }
        List<ComparisonFiscalYearResponse> fiscalYears = new ArrayList<>();
        for (int i = 0; i < c.fiscalYears().size(); i++) {
            var ex = c.fiscalYears().get(i);
            fiscalYears.add(new ComparisonFiscalYearResponse(ex.id(), ex.label(), c.summary().get(i).execution(),
                    ex.period(), ex.type() == FiscalYearType.PRINTED_COLUMN ? null : ex.budgetId()));
        }
        List<ComparisonGroupResponse> groups = new ArrayList<>();
        for (ComparedGroupResponse g : c.groups()) {
            groups.add(new ComparisonGroupResponse(g.code(), g.description(),
                    g.values().stream().map(ComparedValueResponse::monthlyPlanned).toList(),
                    g.values().stream().map(ComparedValueResponse::target).toList()));
        }
        return new IndicatorComparisonResponse(List.copyOf(fiscalYears), List.copyOf(groups));
    }

    private static Instant dataAsOf(Input e) {
        return e.months().stream().map(CalculatedMonth::result).filter(Objects::nonNull)
                .flatMap(r -> r.months().stream()).map(FiscalYearMonthResponse::cashFlows).flatMap(List::stream)
                .map(UsedCashFlowResponse::uploadedAt).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
    }
}
