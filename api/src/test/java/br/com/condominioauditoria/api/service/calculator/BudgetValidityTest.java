package br.com.condominioauditoria.api.service.calculator;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.enums.BudgetStatus;
import br.com.condominioauditoria.api.service.budget.BudgetQueryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** RF-03.1.3: only one budget is valid for each month of each condominium. */
class BudgetValidityTest {

    @Test
    void pilotBudgetIsValidFromMay2026ToApril2027() {
        Budget budget = confirmed(YearMonth.of(2026, 5), YearMonth.of(2027, 4));

        assertThat(BudgetValidity.activeInMonth(List.of(budget), YearMonth.of(2026, 9))).contains(budget);
        assertThat(BudgetValidity.activeInMonth(List.of(budget), YearMonth.of(2026, 5))).contains(budget);
        assertThat(BudgetValidity.activeInMonth(List.of(budget), YearMonth.of(2027, 4))).contains(budget);
        // "sem PO aprovada para este mês"
        assertThat(BudgetValidity.activeInMonth(List.of(budget), YearMonth.of(2026, 4))).isEmpty();
        assertThat(BudgetValidity.activeInMonth(List.of(budget), YearMonth.of(2027, 5))).isEmpty();
    }

    @Test
    void onlyReadBudgetIsNotValidForAnyMonth() {
        Budget read = read();

        assertThat(BudgetValidity.of(read)).isEmpty();
        assertThat(BudgetValidity.activeInMonth(List.of(read), YearMonth.of(2026, 9))).isEmpty();
    }

    @Test
    void supersededIsValidUntilMonthBeforeNewVersion() {
        Budget v1 = confirmed(YearMonth.of(2026, 5), YearMonth.of(2027, 4));
        v1.supersedeFrom(YearMonth.of(2026, 9));

        BudgetValidity validity = BudgetValidity.of(v1).orElseThrow();
        assertThat(validity.end()).isEqualTo(YearMonth.of(2026, 8));
        assertThat(validity.overlaps(YearMonth.of(2026, 9), YearMonth.of(2027, 4))).isFalse();
        assertThat(validity.overlaps(YearMonth.of(2026, 8), YearMonth.of(2027, 4))).isTrue();
    }

    @Test
    void supersededFromStartIsNoLongerValid() {
        Budget v1 = confirmed(YearMonth.of(2026, 5), YearMonth.of(2027, 4));
        v1.supersedeFrom(YearMonth.of(2026, 5));

        assertThat(BudgetValidity.of(v1)).isEmpty();
    }

    private static Budget read() {
        Budget p = new Budget(UUID.randomUUID(), UUID.randomUUID(), "a".repeat(64));
        p.recordReading("po-protest", "t", "e", "a", "b", BudgetStatus.LIDA, BigDecimal.TEN, BigDecimal.ONE,
                BigDecimal.ONE, new BigDecimal("0.01"), Instant.now());
        return p;
    }

    private static Budget confirmed(YearMonth start, YearMonth end) {
        Budget p = read();
        p.confirm(1, start, end, null, true, null, false, null, "admin", Instant.now());
        return p;
    }

    @Test
    void outsideFirstQuarterByMinutesDateOrFiscalYearStart() {
        Budget may = read();
        may.confirm(1, YearMonth.of(2026, 5), YearMonth.of(2027, 4), UUID.randomUUID(), false,
                LocalDate.of(2026, 5, 20), false, null, "admin", Instant.now());
        Budget marchWithoutMinutes = confirmed(YearMonth.of(2026, 3), YearMonth.of(2027, 2));

        assertThat(BudgetQueryService.outsideFirstQuarter(may)).isTrue();
        assertThat(BudgetQueryService.outsideFirstQuarter(marchWithoutMinutes)).isFalse();
    }
}
