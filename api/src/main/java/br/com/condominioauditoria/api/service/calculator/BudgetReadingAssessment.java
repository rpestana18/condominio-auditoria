package br.com.condominioauditoria.api.service.calculator;

import static br.com.condominioauditoria.api.util.MoneyFormatter.format;

import br.com.condominioauditoria.api.model.enums.BudgetStatus;
import br.com.condominioauditoria.api.service.calculator.BudgetStructure.Group;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Decides, in the api, what the rag's budget checks mean (RF-03.1.2). Pure function: the same lines, checks and
 * tolerance always give the same result, when saving and when reading.
 *
 * <ul>
 *   <li>A check that passed: {@link CheckClassification#OK}.</li>
 *   <li>{@code CODIGO_REPETIDO}: pending item resolved by the effective code at confirmation; not a sum
 *       discrepancy.</li>
 *   <li>{@code SUBTOTAL_GRUPO}, {@code TOTAL} and {@code PREVISTO_MES} that failed: the api recomputes the sum from the
 *       saved lines. A non-zero difference up to the tolerance is {@link CheckClassification#ARREDONDAMENTO}
 *       (warning); above it, or if the recomputed sum does not confirm the failure, it is
 *       {@link CheckClassification#DIVERGENCIA}.</li>
 *   <li>Any other failed check is a discrepancy.</li>
 * </ul>
 *
 * A discrepancy → status {@link BudgetStatus#LIDA_COM_DIVERGENCIA}; otherwise {@link BudgetStatus#LIDA}.
 */
public final class BudgetReadingAssessment {

    public static final String GROUP_SUBTOTAL = "SUBTOTAL_GRUPO";
    public static final String TOTAL = "TOTAL";
    public static final String MONTHLY_PLANNED = "PREVISTO_MES";
    public static final String REPEATED_CODE = "CODIGO_REPETIDO";

    public enum CheckClassification {
        OK, ARREDONDAMENTO, DIVERGENCIA, CODIGO_REPETIDO
    }

    /** Check as it came from the rag. */
    public record BudgetCheck(String code, String description, boolean ok, String detail) {
    }

    /** Check with the api's classification and, when it failed, the text with both sums. */
    public record AssessedCheck(BudgetCheck check, CheckClassification classification, String explanation) {
    }

    public record Result(BudgetStatus status, List<AssessedCheck> checks) {

        public List<String> roundings() {
            return explanations(CheckClassification.ARREDONDAMENTO);
        }

        public List<String> discrepancies() {
            return explanations(CheckClassification.DIVERGENCIA);
        }

        public boolean hasRepeatedCode() {
            return checks.stream().anyMatch(c -> c.classification() == CheckClassification.CODIGO_REPETIDO);
        }

        private List<String> explanations(CheckClassification c) {
            return checks.stream().filter(a -> a.classification() == c).map(AssessedCheck::explanation)
                    .toList();
        }
    }

    private BudgetReadingAssessment() {
    }

    public static Result assess(BudgetStructure structure, List<BudgetCheck> checks, BigDecimal tolerance) {
        List<AssessedCheck> assessed = new ArrayList<>();
        int groupIndex = 0;
        for (BudgetCheck c : checks) {
            Group group = null;
            if (GROUP_SUBTOTAL.equals(c.code())) {
                // The rag emits one SUBTOTAL_GRUPO per group, in document order
                group = groupIndex < structure.groups().size() ? structure.groups().get(groupIndex) : null;
                groupIndex++;
            }
            assessed.add(assess(c, group, structure, tolerance));
        }
        boolean divergent = assessed.stream().anyMatch(a -> a.classification() == CheckClassification.DIVERGENCIA);
        return new Result(divergent ? BudgetStatus.LIDA_COM_DIVERGENCIA : BudgetStatus.LIDA,
                List.copyOf(assessed));
    }

    private static AssessedCheck assess(BudgetCheck c, Group group, BudgetStructure e, BigDecimal tolerance) {
        if (c.ok()) {
            return new AssessedCheck(c, CheckClassification.OK, null);
        }
        return switch (c.code()) {
            case REPEATED_CODE -> new AssessedCheck(c, CheckClassification.CODIGO_REPETIDO, c.detail());
            case GROUP_SUBTOTAL -> group == null
                    ? discrepancy(c, c.detail())
                    : byDifference(c, group.difference(), tolerance, "%s impresso %s; soma das linhas %s".formatted(
                            group.name(), format(group.line().getBudgeted()), format(group.linesSum())));
            case TOTAL -> e.total() == null
                    ? discrepancy(c, c.detail())
                    : byDifference(c, e.total().getBudgeted().subtract(e.printedSubtotalsSum()), tolerance,
                            "Total impresso %s; soma dos grupos %s".formatted(format(e.total().getBudgeted()),
                                    format(e.printedSubtotalsSum())));
            case MONTHLY_PLANNED -> e.printedMonthlyPlanned()
                    .map(p -> byDifference(c, p.subtract(e.printedSubtotalsSumWithoutFunds()), tolerance,
                            "Previsto do mês impresso (total menos fundos) %s; soma dos demais grupos %s"
                                    .formatted(format(p), format(e.printedSubtotalsSumWithoutFunds()))))
                    .orElseGet(() -> discrepancy(c, c.detail()));
            default -> discrepancy(c, c.detail());
        };
    }

    private static AssessedCheck byDifference(BudgetCheck c, BigDecimal difference, BigDecimal tolerance,
            String sums) {
        if (difference.signum() == 0) {
            // The recomputed sum matches, but the rag reported a failure: it cannot be explained by rounding
            return discrepancy(c, sums + " (" + c.detail() + ")");
        }
        if (difference.abs().compareTo(tolerance) <= 0) {
            return new AssessedCheck(c, CheckClassification.ARREDONDAMENTO, sums + "; diferença de "
                    + format(difference.abs()) + " tratada como arredondamento; os cálculos usam a soma das linhas");
        }
        return discrepancy(c, sums);
    }

    private static AssessedCheck discrepancy(BudgetCheck c, String explanation) {
        return new AssessedCheck(c, CheckClassification.DIVERGENCIA, explanation);
    }
}
