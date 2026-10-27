package br.com.condominioauditoria.api.service.calculator;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.budget.BudgetLineOrigin;
import br.com.condominioauditoria.api.model.enums.BudgetLineType;
import br.com.condominioauditoria.api.model.enums.BudgetLineWarningCode;
import br.com.condominioauditoria.api.model.enums.OriginMethod;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** RF-13.12 (ADR 0009, Decision 1): warnings of the line review, synthetic lines with the pilot's cases. */
class BudgetLineReviewCheckTest {

    private final Budget budget = new Budget(UUID.randomUUID(), UUID.randomUUID(), "a".repeat(64));
    private final List<BudgetLine> lines = new ArrayList<>();

    private BudgetLine add(BudgetLineType type, String code, String account, String description, String amount) {
        BudgetLine l = new BudgetLine(budget, lines.size() + 1, 1, type, code, account, account, null, description,
                null, new BigDecimal(amount), null, null);
        lines.add(l);
        return l;
    }

    private List<BudgetLineWarningCode> codes(Map<UUID, List<BudgetLineReviewCheck.Warning>> w, BudgetLine l) {
        return w.get(l.getId()).stream().map(BudgetLineReviewCheck.Warning::code).toList();
    }

    @Test
    void warningsOfThePilotCases() {
        add(BudgetLineType.GROUP, "1.1", null, "Pessoal", "100.00");
        BudgetLine staff = add(BudgetLineType.LINE, "1.1.1", "1224 - Verbas Salariais", "Salários", "50.00");
        BudgetLine bonus = add(BudgetLineType.LINE, "1.1.2", "1240 - Gratificações", "Vale transporte", "50.00");
        add(BudgetLineType.GROUP, "1.3", null, "Contratos", "30.00");
        BudgetLine a = add(BudgetLineType.LINE, "1.3.2", "1600 - Manutenção Elevadores", "Atlas", "10.00");
        BudgetLine b = add(BudgetLineType.LINE, "1.3.2", "1600 - Manutenção Elevadores", "Thyssen", "20.00");
        BudgetLine zero = add(BudgetLineType.LINE, "1.3.3", "1610 - Seguro", "Seguro", "0.00");

        var w = BudgetLineReviewCheck.check(BudgetStructure.of(lines), Map.of());

        assertThat(codes(w, staff)).containsExactly(BudgetLineWarningCode.GROUP_ACCOUNT_SUGGESTED);
        assertThat(codes(w, bonus)).containsExactly(BudgetLineWarningCode.ACCOUNT_DESCRIPTION_MISMATCH);
        // In a contracts group the description is the supplier: no O1
        assertThat(codes(w, a)).containsExactly(BudgetLineWarningCode.ACCOUNT_IN_TWO_LINES,
                BudgetLineWarningCode.REPEATED_ITEM);
        assertThat(codes(w, b)).containsExactly(BudgetLineWarningCode.ACCOUNT_IN_TWO_LINES,
                BudgetLineWarningCode.REPEATED_ITEM);
        assertThat(codes(w, zero)).containsExactly(BudgetLineWarningCode.ZERO_BUDGETED);

        staff.setMarks(true, false);
        var marked = BudgetLineReviewCheck.check(BudgetStructure.of(lines), Map.of());
        assertThat(codes(marked, staff)).containsExactly(BudgetLineWarningCode.GROUP_ACCOUNT);
    }

    @Test
    void originFinalAmountDifferentFromTheBudgetIsAWarningAndTheBudgetHolds() {
        add(BudgetLineType.GROUP, "1.3", null, "Serviços", "100.00");
        BudgetLine l = add(BudgetLineType.LINE, "1.3.10", "1692 - Vigilância", "Vigilância", "80022.96");
        BudgetLineOrigin same = new BudgetLineOrigin(l);
        same.set(List.of("1692"), null, null, new BigDecimal("74627.40"), new BigDecimal("74627.40"),
                OriginMethod.AVERAGE_TIMES_INDEX, "VIGILANTES RJ", new BigDecimal("0.0723"), false, null,
                new BigDecimal("80022.97"), null);
        assertThat(codes(BudgetLineReviewCheck.check(BudgetStructure.of(lines), Map.of(l.getId(), same)), l))
                .isEmpty();

        BudgetLineOrigin differs = new BudgetLineOrigin(l);
        differs.set(List.of("1692"), null, null, null, null, OriginMethod.TYPED, null, null, false, null,
                new BigDecimal("80000.00"), null);
        var w = BudgetLineReviewCheck.check(BudgetStructure.of(lines), Map.of(l.getId(), differs));
        assertThat(codes(w, l)).containsExactly(BudgetLineWarningCode.ORIGIN_FINAL_DIFFERS);
        assertThat(w.get(l.getId()).getFirst().message()).contains("vale o da PO");
    }

    @Test
    void accountCodesAndO1Words() {
        assertThat(BudgetLineReviewCheck.accountCodes("1591 - Telefone Fixo + 1592 - Telefone Móvel"))
                .containsExactly("1591", "1592");
        assertThat(BudgetLineReviewCheck.accountCodes(null)).isEmpty();
        assertThat(BudgetLineReviewCheck.matches("1442 - Vigilância", "Vigilantes")).isTrue();
        assertThat(BudgetLineReviewCheck.matches("1240 - Gratificações", "Vale transporte")).isFalse();
        assertThat(BudgetLineReviewCheck.matches(null, "Qualquer")).isTrue();
    }
}
