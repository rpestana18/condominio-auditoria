package br.com.condominioauditoria.api.service.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.condominioauditoria.api.dto.response.budget.ComparisonFiscalYearResponse;
import br.com.condominioauditoria.api.dto.response.budget.IndicatorsResponse;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.enums.MonthStatus;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * RF-11.10 and RF-11.11 without the golden: months without a cash flow, fiscal year default and printed column
 * comparison.
 */
class IndicatorServiceTest {

    private final BudgetScenario scenario = new BudgetScenario();

    @Test
    void withoutCashFlowNoPointAtZero() {
        Budget budget = scenario.confirmedBudget();

        IndicatorsResponse r = scenario.indicators.indicators(scenario.condominiumId, null, null);

        assertThat(r.budgetId()).isEqualTo(budget.getId());
        assertThat(r.start()).isEqualTo("2026-05");
        assertThat(r.monthlyExecution()).hasSize(12).allMatch(p -> p.status() == MonthStatus.NO_CASH_FLOW
                && p.execution() == null && p.planned() == null && p.target() == null);
        assertThat(r.rule20()).allMatch(p -> p.percentage() == null);
        assertThat(r.cumulative()).allMatch(p -> p.cumulativeActual() == null);
        assertThat(r.largestDifferences().above()).isEmpty();
        assertThat(r.comparison()).isNull();
        assertThat(r.dataAsOf()).isNull();
        assertThat(r.warnings()).containsExactly(
                "12 meses do exercício sem números (sem fluxo carregado ou com dois fluxos)",
                "Comparação entre exercícios: sem exercício anterior a este");
    }

    @Test
    void monthWithCashFlowAndComparisonWithPrintedColumn() {
        Budget budget = scenario.readBudget(PilotBudget.defaults().withPreviousColumn());
        scenario.confirmation.confirm(scenario.condominiumId, budget.getId(), scenario.pilotRequest(budget), "admin");
        scenario.cashFlow("fluxo-2026-09.pdf", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 0);

        IndicatorsResponse r = scenario.indicators.indicators(scenario.condominiumId, budget.getId(), null);

        assertThat(r.monthlyExecution().get(4).status()).isEqualTo(MonthStatus.WITH_CASH_FLOW);
        assertThat(r.monthlyExecution().get(4).planned()).isEqualByComparingTo("451620.13");
        assertThat(r.monthlyExecution().get(4).actual()).isEqualByComparingTo("0.00");
        assertThat(r.cumulative().get(4).cumulativePlanned()).isEqualByComparingTo("451620.13");
        assertThat(r.dataAsOf()).isNotNull();
        assertThat(r.comparison().fiscalYears()).extracting(ComparisonFiscalYearResponse::label)
                .containsExactly("2026/2027", "2025/2026 (coluna impressa)");
        assertThat(r.comparison().groups()).filteredOn(gr -> gr.code().equals("1.3")).singleElement()
                .satisfies(gr -> assertThat(gr.monthlyPlanned()).extracting(java.math.BigDecimal::toPlainString)
                        .containsExactly("336274.18", "348631.55"));
        // RF-11.12: the click opens the group evidence; the printed column has no actual, so neither budget nor target
        assertThat(r.comparison().fiscalYears()).extracting(ComparisonFiscalYearResponse::budgetId)
                .containsExactly(budget.getId(), null);
        assertThat(r.comparison().groups()).allSatisfy(gr -> assertThat(gr.targets()).hasSize(2).last().isNull());
        assertThat(r.comparison().groups()).filteredOn(gr -> gr.code().equals("1.3")).singleElement()
                .satisfies(gr -> assertThat(gr.targets().getFirst()).startsWith("group:"));
        assertThat(r.warnings()).containsExactly(
                "11 meses do exercício sem números (sem fluxo carregado ou com dois fluxos)");
    }

    @Test
    void withoutConfirmedBudgetOrBudgetOfOtherCondominium() {
        assertThatThrownBy(() -> scenario.indicators.indicators(scenario.condominiumId, null, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        scenario.confirmedBudget();
        assertThatThrownBy(() -> scenario.indicators.indicators(scenario.condominiumId, UUID.randomUUID(), null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
