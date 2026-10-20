package br.com.condominioauditoria.api.service.calculator;

import static br.com.condominioauditoria.api.service.calculator.SyntheticSeptember.OCTOBER;
import static br.com.condominioauditoria.api.service.calculator.SyntheticSeptember.OPERATING;
import static br.com.condominioauditoria.api.service.calculator.SyntheticSeptember.SEPTEMBER;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualWarningResponse;
import br.com.condominioauditoria.api.dto.response.budget.FiscalYearMonthResponse;
import br.com.condominioauditoria.api.model.enums.BudgetVsActualStatus;
import br.com.condominioauditoria.api.model.enums.MonthStatus;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.Cumulative;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.Month;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * RF-13.10 in the existing budget vs. actual: a partial month keeps its own numbers, but stays out of the fiscal year
 * cumulative and of the 20% rule, with the PARTIAL_MONTH warning. Without a partial month, nothing changes.
 */
class BudgetVsActualPartialMonthTest {

    private final SyntheticSeptember s = new SyntheticSeptember();

    @Test
    void withoutPartialMonthTheCumulativeIsUnchanged() {
        BudgetVsActualResponse r = cumulative();

        assertThat(r.calculationVersion()).isEqualTo(BudgetVsActualCalculator.VERSION);
        assertThat(r.summedMonths()).containsExactly("2026-09");
        assertThat(r.totals().inLines()).isEqualByComparingTo("445125.96");
        assertThat(r.months()).allSatisfy(m -> assertThat(m.partialUntil()).isNull());
        assertThat(r.warnings()).noneSatisfy(w -> assertThat(w.code()).isEqualTo("PARTIAL_MONTH"));
    }

    @Test
    void partialMonthIsShownButOutOfTheCumulative() {
        addPartialOctober();
        BudgetVsActualResponse before = new SyntheticSeptember().calculate(new Cumulative());

        BudgetVsActualResponse r = cumulative();

        assertThat(r.status()).isEqualTo(BudgetVsActualStatus.CALCULATED);
        assertThat(r.summedMonths()).containsExactly("2026-09");
        // Same totals as with September alone
        assertThat(r.totals().inLines()).isEqualByComparingTo(before.totals().inLines());
        assertThat(r.totals().actualExpense()).isEqualByComparingTo(before.totals().actualExpense());
        assertThat(r.totals().planned()).isEqualByComparingTo(before.totals().planned());
        FiscalYearMonthResponse oct = month(r, "2026-10");
        assertThat(oct.status()).isEqualTo(MonthStatus.WITH_CASH_FLOW);
        assertThat(oct.partialUntil()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(oct.actualExpense()).isEqualByComparingTo("90000.00");
        assertThat(oct.aboveLimit()).isNull();
        assertThat(month(r, "2026-09").partialUntil()).isNull();
        assertThat(month(r, "2026-09").aboveLimit()).isNotNull();
        // October is after the last complete month, but it is not "sem fluxo"
        assertThat(r.monthsWithoutCashFlow()).doesNotContain("2026-10");
        assertThat(r.warnings()).filteredOn(w -> w.code().equals("PARTIAL_MONTH")).singleElement()
                .extracting(BudgetVsActualWarningResponse::text)
                .isEqualTo("Mês parcial: 10/2026 (lançamentos até 20/10/2026) fica fora do acumulado e da regra"
                        + " dos 20%");
    }

    @Test
    void partialMonthAloneHasItsNumbersWithoutThe20PercentRule() {
        addPartialOctober();

        BudgetVsActualResponse r = s.calculate(new Month(OCTOBER));

        assertThat(r.status()).isEqualTo(BudgetVsActualStatus.CALCULATED);
        assertThat(r.totals().actualExpense()).isEqualByComparingTo("90000.00");
        assertThat(r.rule20()).isNull();
        assertThat(r.months().getFirst().partialUntil()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(r.warnings()).anySatisfy(w -> {
            assertThat(w.code()).isEqualTo("PARTIAL_MONTH");
            assertThat(w.text()).endsWith("a regra dos 20% não é avaliada");
        });
        // September is still a complete month with the rule
        assertThat(s.calculate(new Month(SEPTEMBER)).rule20()).isNotNull();
    }

    @Test
    void onlyPartialMonthsLeaveTheCumulativeWithoutNumbers() {
        UUID september = s.cashFlows.getFirst().fileId();
        s.partialUntil.put(september, LocalDate.of(2026, 9, 25));

        BudgetVsActualResponse r = cumulative();

        assertThat(r.status()).isEqualTo(BudgetVsActualStatus.NO_CASH_FLOW);
        assertThat(r.message()).isEqualTo("Nenhum mês completo do exercício: set/2026 é parcial");
        assertThat(r.summedMonths()).isEmpty();
        assertThat(month(r, "2026-09").partialUntil()).isEqualTo(LocalDate.of(2026, 9, 25));
        assertThat(r.warnings()).anySatisfy(w -> assertThat(w.code()).isEqualTo("PARTIAL_MONTH"));
    }

    private void addPartialOctober() {
        UUID october = UUID.randomUUID();
        s.cashFlows.add(SyntheticSeptember.cashFlow(october, OCTOBER, OCTOBER.atEndOfMonth()));
        s.partialUntil.put(october, LocalDate.of(2026, 10, 20));
        s.entries.add(s.entry(october, OPERATING, s.account("1.1.1"), "0.00", "90000.00", false, false,
                OCTOBER.atDay(10)));
    }

    private BudgetVsActualResponse cumulative() {
        return s.calculate(new Cumulative());
    }

    private static FiscalYearMonthResponse month(BudgetVsActualResponse r, String month) {
        return r.months().stream().filter(m -> m.month().equals(month)).findFirst().orElseThrow();
    }
}
