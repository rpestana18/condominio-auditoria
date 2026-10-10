package br.com.condominioauditoria.rag.service.calculator;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.rag.model.TotalsCheck;
import br.com.condominioauditoria.rag.model.budget.Budget;
import br.com.condominioauditoria.rag.model.budget.BudgetLine;
import br.com.condominioauditoria.rag.model.enums.BudgetLineType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Budget check with a small hand-built budget (runs without the pilot data). */
public class BudgetCheckTest {

    @Test
    public void balancedBudgetPassesEverything() {
        var budget = budget(
                line(BudgetLineType.TOTAL, "1", "Soma das seções 1.1 a 1.9", "TOTAL DAS DESPESAS", "1050.00", null),
                line(BudgetLineType.GROUP, "1.1", "Subtotal (soma linhas 3 a 4)", "PESSOAL", "1000.00", null),
                line(BudgetLineType.LINE, "1.1.1", null, "Salários", "600.00", null),
                line(BudgetLineType.LINE, "1.1.2", null, "Férias", "400.00", null),
                line(BudgetLineType.GROUP, "1.9", "Fundos", "Fundos do Condomínio", "50.00", null),
                line(BudgetLineType.LINE, "1.9.1", "Fundo de Reserva", "Fundo de Reserva", "30.00", "3,00%"),
                line(BudgetLineType.LINE, "1.9.2", "Obras", "Fundo de Obras", "20.00", "2,00%"));

        List<TotalsCheck> result = BudgetCheck.check(budget);

        assertThat(result).allSatisfy(v -> assertThat(v.ok()).as(v.code() + ": " + v.detail()).isTrue());
        assertThat(result).extracting(TotalsCheck::code).containsExactly("GROUP_SUBTOTAL", "GROUP_SUBTOTAL",
                "TOTAL", "MONTHLY_PLANNED", "FUND_RATE", "REPEATED_CODE");
        assertThat(BudgetCheck.monthlyPlanned(budget)).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("1000.00"));
        assertThat(result.get(3).detail()).isEqualTo("1.050,00 - 50,00 = 1.000,00");
        assertThat(result.get(5).detail()).isEqualTo("nenhum código repetido");
    }

    @Test
    public void subtotalDifferentFromSumShowsBothSums() {
        var budget = budget(
                line(BudgetLineType.TOTAL, "1", null, "TOTAL", "1000.00", null),
                line(BudgetLineType.GROUP, "1.1", "Subtotal", "PESSOAL", "999.00", null),
                line(BudgetLineType.LINE, "1.1.1", null, "Salários", "1000.00", null));

        List<TotalsCheck> result = BudgetCheck.check(budget);

        assertThat(result.getFirst().ok()).isFalse();
        assertThat(result.getFirst().detail())
                .isEqualTo("1.1 PESSOAL: soma das linhas 1.000,00; impresso 999,00; diferença -1,00");
        TotalsCheck total = result.stream().filter(v -> v.code().equals("TOTAL")).findFirst().orElseThrow();
        assertThat(total.ok()).isFalse();
        assertThat(total.detail()).isEqualTo("soma dos grupos 999,00; impresso 1.000,00; diferença 1,00");
        TotalsCheck planned = result.stream().filter(v -> v.code().equals("MONTHLY_PLANNED")).findFirst().orElseThrow();
        assertThat(planned.ok()).isFalse();
        assertThat(planned.detail()).isEqualTo("grupo de fundos não encontrado");
    }

    /** The same code on two lines: both stay and add up in the group, and the code is pointed out. */
    @Test
    public void repeatedCodeIsPointedOutAndBothLinesAdd() {
        var budget = budget(
                line(BudgetLineType.GROUP, "1.3", "Subtotal", "CONTRATOS", "4518.93", null),
                line(BudgetLineType.LINE, "1.3.2", "1598 - Bombas", "Servirio", "3000.00", null),
                line(BudgetLineType.LINE, "1.3.24", "4069 - ASSESSORIA", "Vitor", "0.00", null),
                line(BudgetLineType.LINE, "1.3.2", "1624 - Caixa D'água", "Caixa D'água", "1518.93", null));

        List<TotalsCheck> result = BudgetCheck.check(budget);

        assertThat(result.getFirst().ok()).isTrue();
        TotalsCheck repeated = result.getLast();
        assertThat(repeated.code()).isEqualTo("REPEATED_CODE");
        assertThat(repeated.ok()).isFalse();
        assertThat(repeated.detail()).isEqualTo("1.3.2 aparece 2 vezes (ordens 2 e 4)");
    }

    @Test
    public void fundOutsideRateFails() {
        var budget = budget(
                line(BudgetLineType.TOTAL, "1", null, "TOTAL", "1060.00", null),
                line(BudgetLineType.GROUP, "1.1", "Subtotal", "PESSOAL", "1000.00", null),
                line(BudgetLineType.LINE, "1.1.1", null, "Salários", "1000.00", null),
                line(BudgetLineType.GROUP, "1.9", "Fundos", "Fundos", "60.00", null),
                line(BudgetLineType.LINE, "1.9.1", "Fundo de Reserva", "Fundo de Reserva", "60.00", "5,00%"));

        TotalsCheck rate = BudgetCheck.check(budget).stream().filter(v -> v.code().equals("FUND_RATE")).findFirst()
                .orElseThrow();

        assertThat(rate.ok()).isFalse();
        assertThat(rate.detail()).isEqualTo("1.9.1 Fundo de Reserva: 5,00% de 1.000,00 = 50,00; impresso 60,00");
    }

    @Test
    public void lineBeforeFirstGroupIsPointedOut() {
        var budget = budget(line(BudgetLineType.LINE, "1.1.1", null, "Solta", "10.00", null));

        assertThat(BudgetCheck.check(budget)).filteredOn(v -> v.code().equals("LINE_WITHOUT_GROUP"))
                .singleElement().satisfies(v -> {
                    assertThat(v.ok()).isFalse();
                    assertThat(v.detail()).isEqualTo("linhas antes do primeiro grupo: 1.1.1");
                });
    }

    @Test
    public void rateFromPercentageColumn() {
        assertThat(BudgetCheck.rate("3,00%")).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("3.00"));
        assertThat(BudgetCheck.rate("-100,00%")).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("-100"));
        assertThat(BudgetCheck.rate("média")).isEmpty();
        assertThat(BudgetCheck.rate(null)).isEmpty();
    }

    private static Budget budget(BudgetLine... lines) {
        List<BudgetLine> numbered = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            BudgetLine l = lines[i];
            numbered.add(new BudgetLine(i + 1, 1, l.type(), l.printedCode(), l.account(), l.accountText(), l.mark(),
                    l.description(), l.previousBudgeted(), l.budgeted(), l.percentageText(), l.notes()));
        }
        return new Budget("PROPOSTA ORÇAMENTÁRIA 2026 / 2027", "2026 / 2027",
                List.of("2025/2026", "2026/2027"), numbered);
    }

    private static BudgetLine line(BudgetLineType type, String code, String accountText, String description,
            String budgeted,
            String percentage) {
        return new BudgetLine(0, 1, type, code, null, accountText, null, description, new BigDecimal("0.00"),
                new BigDecimal(budgeted), percentage, null);
    }
}
