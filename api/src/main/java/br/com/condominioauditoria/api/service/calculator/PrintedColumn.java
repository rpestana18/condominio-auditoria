package br.com.condominioauditoria.api.service.calculator;

import static br.com.condominioauditoria.api.util.MoneyFormatter.format;

import br.com.condominioauditoria.api.dto.response.budget.GroupDifferenceResponse;
import br.com.condominioauditoria.api.dto.response.budget.PrintedColumnGroupResponse;
import br.com.condominioauditoria.api.dto.response.budget.PrintedColumnLineResponse;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * "PO anterior pela coluna impressa" (RF-11.5; ADR 0005, Decision 3): the "Orçado anterior" column of a confirmed
 * budget becomes a fiscal year with planned only, built at query time, without a table. Pure function.
 *
 * <p>Column check as in RF-03.1.2 (budget tolerance, R$ 0,01): sum of the lines of each group against the printed
 * subtotal, and printed total against the sum of the subtotals. Groups, funds and month planned follow the sum of the
 * lines, as in the confirmed budget (Q29); a mismatch becomes a warning. In this column the printed total may leave
 * the funds out (in the pilot, 441.304,38 is the sum of groups 1.1 to 1.8): the check accepts both forms and says
 * which one held.
 */
public record PrintedColumn(UUID budgetId, String label, BigDecimal printedTotal, boolean totalIncludesFunds,
        BigDecimal funds, BigDecimal monthlyPlanned, List<PrintedColumnGroupResponse> groups, List<String> warnings) {

    /**
     * Builds the budget's "Orçado anterior" column. Empty when the budget did not print the column (no label or with
     * all values zeroed).
     */
    public static Optional<PrintedColumn> of(Budget budget, List<BudgetLine> lines) {
        if (budget.getPreviousBudgetedColumn() == null || budget.getPreviousBudgetedColumn().isBlank()
                || lines.stream().allMatch(l -> l.getPreviousBudgeted() == null || l.getPreviousBudgeted().signum() == 0)) {
            return Optional.empty();
        }
        BigDecimal tolerance = budget.getRoundingTolerance() == null ? new BigDecimal("0.01")
                : budget.getRoundingTolerance();
        BudgetStructure structure = BudgetStructure.of(lines);
        List<String> warnings = new ArrayList<>();
        List<PrintedColumnGroupResponse> groups = new ArrayList<>();
        BigDecimal printedWithFunds = zero();
        BigDecimal printedWithoutFunds = zero();
        BigDecimal planned = zero();
        BigDecimal funds = zero();
        for (BudgetStructure.Group g : structure.groups()) {
            List<PrintedColumnLineResponse> ofGroup = g.lines().stream().map(PrintedColumn::line).toList();
            BigDecimal sum = ofGroup.stream().map(PrintedColumnLineResponse::amount).reduce(zero(), BigDecimal::add);
            BigDecimal printed = amount(g.line());
            BigDecimal difference = printed.subtract(sum);
            boolean matches = difference.abs().compareTo(tolerance) <= 0;
            groups.add(new PrintedColumnGroupResponse(g.line().getId(), g.line().getEffectiveCode(),
                    g.line().getDescription(),
                    g.funds(), printed, sum, difference, matches, ofGroup));
            if (!matches) {
                warnings.add("Grupo " + g.name() + ": subtotal impresso " + format(printed) + "; soma das linhas "
                        + format(sum) + " (diferença " + format(sum.subtract(printed)) + "). Vale a soma das"
                        + " linhas.");
            }
            printedWithFunds = printedWithFunds.add(printed);
            if (g.funds()) {
                funds = funds.add(sum);
            } else {
                printedWithoutFunds = printedWithoutFunds.add(printed);
                planned = planned.add(sum);
            }
        }
        BigDecimal total = structure.total() == null ? null : amount(structure.total());
        boolean includesFunds = true;
        if (total != null) {
            if (total.subtract(printedWithFunds).abs().compareTo(tolerance) <= 0) {
                includesFunds = true;
            } else if (structure.funds().isPresent()
                    && total.subtract(printedWithoutFunds).abs().compareTo(tolerance) <= 0) {
                includesFunds = false;
                warnings.add("Nesta coluna o total impresso (" + format(total) + ") não inclui os fundos: confere com a"
                        + " soma dos subtotais sem os fundos (" + format(printedWithoutFunds) + ").");
            } else {
                warnings.add("Total impresso " + format(total) + " não confere com a soma dos subtotais ("
                        + format(printedWithFunds) + " com os fundos; " + format(printedWithoutFunds)
                        + " sem os fundos). Vale a soma das linhas.");
            }
        }
        return Optional.of(new PrintedColumn(budget.getId(),
                budget.getPreviousBudgetedColumn().trim() + " (coluna impressa)",
                total, includesFunds, funds, planned, List.copyOf(groups), List.copyOf(warnings)));
    }

    /**
     * Differences above the tolerance, per group, between the uploaded previous budget (sum of each group's lines) and
     * this column (RF-11.5). Groups matched by code (1.1 to 1.9); a group that exists on only one side is not compared.
     */
    public List<GroupDifferenceResponse> differences(List<BudgetLine> previousBudgetLines, BigDecimal tolerance) {
        Map<String, BigDecimal> uploaded = new LinkedHashMap<>();
        for (BudgetStructure.Group g : BudgetStructure.of(previousBudgetLines).groups()) {
            uploaded.put(g.line().getEffectiveCode(), g.linesSum());
        }
        List<GroupDifferenceResponse> list = new ArrayList<>();
        for (PrintedColumnGroupResponse g : groups) {
            BigDecimal amount = uploaded.get(g.code());
            if (amount != null && amount.subtract(g.amount()).abs().compareTo(tolerance) > 0) {
                list.add(new GroupDifferenceResponse(g.code(), g.description(), amount, g.amount()));
            }
        }
        return List.copyOf(list);
    }

    /** Line by effective code (the first one, if repeated). */
    public Optional<PrintedColumnLineResponse> line(String effectiveCode) {
        return groups.stream().flatMap(g -> g.lines().stream()).filter(l -> l.code().equals(effectiveCode))
                .findFirst();
    }

    private static PrintedColumnLineResponse line(BudgetLine l) {
        return new PrintedColumnLineResponse(l.getId(), l.getEffectiveCode(), l.getAccount(), l.getDescription(),
                amount(l),
                l.getPercentageText());
    }

    private static BigDecimal amount(BudgetLine l) {
        return l.getPreviousBudgeted() == null ? zero() : l.getPreviousBudgeted();
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2);
    }
}
