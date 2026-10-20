package br.com.condominioauditoria.api.service.calculator;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.CashFlowFile;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** RF-13.10: the complete cash flow of the month replaces the partial one, never "dois fluxos para o mês". */
class PartialCashFlowsTest {

    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

    @Test
    void completeReplacesPartial() {
        CashFlowFile partial = SyntheticSeptember.cashFlow(UUID.randomUUID(), OCTOBER, OCTOBER.atEndOfMonth());
        CashFlowFile complete = SyntheticSeptember.cashFlow(UUID.randomUUID(), OCTOBER, OCTOBER.atEndOfMonth());

        var s = PartialCashFlows.select(List.of(partial, complete), Map.of(partial.fileId(), LocalDate.of(2026, 10,
                20)));

        assertThat(s.effective()).containsExactly(complete);
        assertThat(s.partialUntil()).isEmpty();
        assertThat(s.superseded()).containsEntry(partial.fileId(), complete.fileId());
    }

    @Test
    void partialAloneStaysWithItsDate() {
        CashFlowFile partial = SyntheticSeptember.cashFlow(UUID.randomUUID(), OCTOBER, OCTOBER.atEndOfMonth());

        var s = PartialCashFlows.select(List.of(partial), Map.of(partial.fileId(), LocalDate.of(2026, 10, 20)));

        assertThat(s.effective()).containsExactly(partial);
        assertThat(s.partialUntil()).containsEntry(partial.fileId(), LocalDate.of(2026, 10, 20));
        assertThat(s.superseded()).isEmpty();
    }

    @Test
    void twoPartialsOfTheSameMonthAreNotChosenHere() {
        CashFlowFile a = SyntheticSeptember.cashFlow(UUID.randomUUID(), OCTOBER, OCTOBER.atEndOfMonth());
        CashFlowFile b = SyntheticSeptember.cashFlow(UUID.randomUUID(), OCTOBER, OCTOBER.atEndOfMonth());

        var s = PartialCashFlows.select(List.of(a, b), Map.of(a.fileId(), LocalDate.of(2026, 10, 10), b.fileId(),
                LocalDate.of(2026, 10, 20)));

        assertThat(s.effective()).containsExactlyInAnyOrder(a, b);
    }
}
