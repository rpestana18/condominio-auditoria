package br.com.condominioauditoria.api.service.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualResponse;
import br.com.condominioauditoria.api.dto.response.budget.ComparisonFiscalYearResponse;
import br.com.condominioauditoria.api.dto.response.budget.ComparisonGroupResponse;
import br.com.condominioauditoria.api.dto.response.budget.EvidenceResponse;
import br.com.condominioauditoria.api.dto.response.budget.ExecutionPointResponse;
import br.com.condominioauditoria.api.dto.response.budget.FundPointResponse;
import br.com.condominioauditoria.api.dto.response.budget.FundSeriesResponse;
import br.com.condominioauditoria.api.dto.response.budget.IndicatorsResponse;
import br.com.condominioauditoria.api.dto.response.budget.LineDifferenceResponse;
import br.com.condominioauditoria.api.dto.response.budget.Rule20PointResponse;
import br.com.condominioauditoria.api.model.enums.MonthStatus;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * RF-11.10 and RF-11.11 with September/2026 of the pilot (private golden): the chart values are those of the budget
 * vs. actual screen, cent by cent. Skipped without data/golden/privado.
 */
class IndicatorGoldenTest {

    private static final int SEPTEMBER = 4;

    @Test
    void septemberInChartsEqualsBudgetVsActual() {
        SeptemberGolden g = golden();
        g.confirmMap();
        BudgetScenario c = g.scenario;

        IndicatorsResponse r = c.indicators.indicators(c.condominiumId, g.budget.getId(), null);
        BudgetVsActualResponse screen = c.budgetVsActual.get(c.condominiumId, "2026-09", g.budget.getId());

        assertThat(r.label()).isEqualTo("2026/2027");
        assertThat(r.dataAsOf()).isNotNull();
        // Chart 1: 98,8% in September; the other 11 months without a number (never zero)
        assertThat(r.monthlyExecution()).hasSize(12);
        ExecutionPointResponse set = r.monthlyExecution().get(SEPTEMBER);
        assertThat(set.month()).isEqualTo("2026-09");
        assertThat(set.execution()).isEqualByComparingTo("98.8").isEqualByComparingTo(screen.totals().execution());
        assertThat(set.actual()).isEqualByComparingTo("446176.89");
        assertThat(set.planned()).isEqualByComparingTo("451620.13");
        assertThat(r.monthlyExecution()).filteredOn(p -> !p.month().equals("2026-09"))
                .allMatch(p -> p.status() == MonthStatus.SEM_FLUXO && p.execution() == null && p.actual() == null);
        // Chart 2: 8,6%, limit 20%, maximum scenario 8,8% provisional
        Rule20PointResponse rule = r.rule20().get(SEPTEMBER);
        assertThat(rule.percentage()).isEqualByComparingTo("8.6");
        assertThat(rule.overrun()).isEqualByComparingTo("38880.19");
        assertThat(rule.limitPercentage()).isEqualByComparingTo("20");
        assertThat(rule.maxScenarioPercentage()).isEqualByComparingTo("8.8");
        assertThat(rule.provisional()).isTrue();
        assertThat(rule.aboveLimit()).isFalse();
        // Chart 3: the September cumulative is September itself
        assertThat(r.cumulative().get(SEPTEMBER).cumulativeActual()).isEqualByComparingTo("446176.89");
        assertThat(r.cumulative().get(SEPTEMBER - 1).cumulativeActual()).isNull();
        // Chart 4: Contracts in September
        assertThat(r.actualByGroup()).filteredOn(s -> s.code().equals("1.3")).singleElement()
                .satisfies(s -> assertThat(s.points().get(SEPTEMBER).amount()).isEqualByComparingTo("341277.13"));
        assertThat(r.actualByGroup()).noneMatch(s -> s.code().equals("1.9"));
        // Chart 5: 1.3.10 among the furthest above, with +6.793,38 and the line evidence
        LineDifferenceResponse watchman = r.largestDifferences().above().stream().filter(d -> d.code().equals("1.3.10"))
                .findFirst().orElseThrow();
        assertThat(watchman.difference()).isEqualByComparingTo("6793.38");
        assertThat(watchman.target()).isEqualTo("linha:" + watchman.lineId());
        assertThat(c.budgetVsActual.evidence(c.condominiumId, "2026-09", g.budget.getId(), watchman.target()).stream()
                .map(EvidenceResponse::amount).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add))
                .isEqualByComparingTo("86816.34");
        assertThat(r.largestDifferences().above()).hasSizeLessThanOrEqualTo(10);
        assertThat(r.largestDifferences().below()).hasSizeLessThanOrEqualTo(10);
        // Chart 6: Reserve 14.260,79 × 13.548,60 and Works 9.705,06 × 9.032,40
        assertThat(pointOf(r, "FUNDO DE RESERVA").collected()).isEqualByComparingTo("14260.79");
        assertThat(pointOf(r, "FUNDO DE RESERVA").planned()).isEqualByComparingTo("13548.60");
        assertThat(pointOf(r, "OBRAS / REFORMAS / INFRA").collected()).isEqualByComparingTo("9705.06");
        assertThat(pointOf(r, "OBRAS / REFORMAS / INFRA").planned()).isEqualByComparingTo("9032.40");
        // Chart 7: this fiscal year and the budget's own "Orçado anterior" column
        assertThat(r.comparison()).isNotNull();
        assertThat(r.comparison().fiscalYears()).hasSize(2);
        assertThat(r.comparison().fiscalYears().getFirst().execution()).isEqualByComparingTo("98.8");
        assertThat(r.comparison().fiscalYears().get(1).label()).endsWith("(coluna impressa)");
        assertThat(r.comparison().fiscalYears()).extracting(ComparisonFiscalYearResponse::budgetId)
                .containsExactly(g.budget.getId(), null);
        ComparisonGroupResponse g13 = r.comparison().groups().stream().filter(gr -> gr.code().equals("1.3"))
                .findFirst().orElseThrow();
        assertThat(g13.targets()).hasSize(2);
        assertThat(g13.targets().get(1)).isNull();
        assertThat(g13.targets().getFirst()).isEqualTo(r.actualByGroup().stream()
                .filter(s -> s.code().equals("1.3")).findFirst().orElseThrow().points().get(SEPTEMBER).target());
        assertThat(c.budgetVsActual.evidence(c.condominiumId, "2026-09", g.budget.getId(), g13.targets().getFirst()))
                .isNotEmpty();
    }

    @Test
    void reserveFundFilterShowsOnlyItsChart() {
        SeptemberGolden g = golden();
        g.confirmMap();
        BudgetScenario c = g.scenario;

        IndicatorsResponse reserve = c.indicators.indicators(c.condominiumId, null, c.reserveFund.getId());
        IndicatorsResponse condominium = c.indicators.indicators(c.condominiumId, null, c.operatingFund.getId());

        assertThat(reserve.monthlyExecution()).isNull();
        assertThat(reserve.funds()).extracting(FundSeriesResponse::fund).containsExactly("FUNDO DE RESERVA");
        assertThat(condominium.funds()).isNull();
        assertThat(condominium.monthlyExecution().get(SEPTEMBER).execution()).isEqualByComparingTo("98.8");
    }

    private static FundPointResponse pointOf(IndicatorsResponse r, String fund) {
        return r.funds().stream().filter(f -> f.fund().equals(fund)).findFirst().orElseThrow().points()
                .get(SEPTEMBER);
    }

    private static SeptemberGolden golden() {
        Optional<SeptemberGolden> g = SeptemberGolden.load();
        assumeTrue(g.isPresent() && SeptemberGolden.map().isPresent(), "golden privado ausente");
        return g.get();
    }
}
