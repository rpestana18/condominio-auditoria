package br.com.condominioauditoria.api.service.calculator;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.BudgetLineData;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.BudgetLineType;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.enums.BudgetItemSource;
import br.com.condominioauditoria.api.service.budget.BudgetImportService;
import br.com.condominioauditoria.api.service.calculator.BudgetItemSuggestion.Confirmed;
import br.com.condominioauditoria.api.service.calculator.BudgetItemSuggestion.LineWithGroup;
import br.com.condominioauditoria.api.service.calculator.BudgetItemSuggestion.NoSuggestion;
import br.com.condominioauditoria.api.service.calculator.BudgetItemSuggestion.Suggested;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** RF-11.7: a suggestion exists only when a single item matches by budget account and group. Pure function. */
class BudgetItemSuggestionTest {

    private final Budget budget = new Budget(UUID.randomUUID(), UUID.randomUUID(), "a".repeat(64));
    private final Budget other = new Budget(UUID.randomUUID(), UUID.randomUUID(),
            "b".repeat(64));

    @Test
    void accountRepeatedInSameGroupOfThisBudgetHasNoSuggestion() {
        LineWithGroup l1 = line(budget, "1.3.5", "1606 - Aparelhos de Ginástica", "1.3");
        LineWithGroup l2 = line(budget, "1.3.9", "1606 - Aparelhos de Ginástica", "1.3");
        UUID item = UUID.randomUUID();
        List<Confirmed> confirmed = List.of(new Confirmed(line(other, "1.3.5", "1606 - Aparelhos de Ginástica",
                "1.3"), item));

        var r = BudgetItemSuggestion.suggest(List.of(l1, l2), List.of(l1, l2), List.of(), confirmed);

        assertThat(r.values()).allSatisfy(x -> {
            assertThat(x).isInstanceOf(NoSuggestion.class);
            assertThat(x.reason()).contains("aparece em mais de uma linha do grupo 1.3");
        });
    }

    @Test
    void accountInTwoItemsInSameGroupHasNoSuggestion() {
        LineWithGroup newLine = line(budget, "1.3.5", "1606 - Aparelhos de Ginástica", "1.3");
        List<Confirmed> confirmed = List.of(
                new Confirmed(line(other, "1.3.5", "1606 - Aparelhos de Ginástica", "1.3"), UUID.randomUUID()),
                new Confirmed(line(other, "1.3.6", "1606 - Aparelhos de Ginástica", "1.3"), UUID.randomUUID()));

        var r = BudgetItemSuggestion.suggest(List.of(newLine), List.of(newLine), List.of(),
                confirmed).get(newLine.line().getId());

        assertThat(r).isInstanceOf(NoSuggestion.class);
        assertThat(r.reason()).contains("está em 2 rubricas");
    }

    @Test
    void sameAccountInAnotherGroupDoesNotMatch() {
        LineWithGroup newLine = line(budget, "1.7.2", "1606 - Aparelhos de Ginástica", "1.7");
        List<Confirmed> confirmed = List.of(
                new Confirmed(line(other, "1.3.5", "1606 - Aparelhos de Ginástica", "1.3"), UUID.randomUUID()));

        var r = BudgetItemSuggestion.suggest(List.of(newLine), List.of(newLine), List.of(),
                confirmed).get(newLine.line().getId());

        assertThat(r).isInstanceOf(NoSuggestion.class);
        assertThat(r.reason()).startsWith("nenhuma linha confirmada com a conta da PO");
    }

    @Test
    void comparesWithoutAccentOrCaseAndNeverByItemCode() {
        UUID item = UUID.randomUUID();
        LineWithGroup newLine = line(budget, "1.3.7", "1682 - SINDICATURA  PROFISSIONAL", "1.3");
        List<Confirmed> confirmed = List.of(
                new Confirmed(line(other, "1.3.20", "1682 - Sindicatura Profissional", "1.3"), item));

        var r = BudgetItemSuggestion.suggest(List.of(newLine), List.of(newLine), List.of(),
                confirmed).get(newLine.line().getId());

        assertThat(r).isEqualTo(new Suggested(item, BudgetItemSource.BUDGET_ACCOUNT,
                "mesma conta da PO e mesmo grupo: 1682 - SINDICATURA  PROFISSIONAL, 1.3"));
    }

    @Test
    void previousVersionComesBeforeBudgetAccount() {
        UUID fromPrevious = UUID.randomUUID();
        LineWithGroup newLine = line(budget, "1.3.20", "1682 - Sindicatura Profissional", "1.3");
        Confirmed previous = new Confirmed(line(other, "1.3.20", "1682 - Sindicatura Profissional", "1.3"),
                fromPrevious);
        Confirmed fromOtherFiscalYear = new Confirmed(line(other, "1.3.21", "1682 - Sindicatura Profissional", "1.3"),
                UUID.randomUUID());

        var r = BudgetItemSuggestion.suggest(List.of(newLine), List.of(newLine), List.of(previous),
                List.of(previous, fromOtherFiscalYear)).get(newLine.line().getId());

        assertThat(r).isInstanceOfSatisfying(Suggested.class, s -> {
            assertThat(s.budgetItemId()).isEqualTo(fromPrevious);
            assertThat(s.source()).isEqualTo(BudgetItemSource.PREVIOUS_VERSION);
        });
    }

    private static LineWithGroup line(Budget budget, String code, String account, String group) {
        BudgetLine l = BudgetImportService.line(budget, new BudgetLineData(1, 1, BudgetLineType.LINE, code, account,
                null, null,
                "Fornecedor " + code, BigDecimal.ZERO.setScale(2), new BigDecimal("100.00"), null, null));
        return new LineWithGroup(l, group);
    }
}
