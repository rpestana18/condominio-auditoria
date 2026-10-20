package br.com.condominioauditoria.api.service.calculator;

import br.com.condominioauditoria.api.model.budget.BudgetLine;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;

/**
 * Total, groups and lines of the budget in document order (same rule as the rag's check): a group's lines are the ones
 * after the GROUP line, not the ones with the code prefix, which may be repeated. Pure function.
 */
public record BudgetStructure(BudgetLine total, List<Group> groups, List<BudgetLine> ungrouped) {

    public record Group(BudgetLine line, List<BudgetLine> lines, boolean funds) {

        public BigDecimal linesSum() {
            return lines.stream().map(BudgetLine::getBudgeted).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        /** Impresso − soma das linhas. Zero quando bate. */
        public BigDecimal difference() {
            return line.getBudgeted().subtract(linesSum());
        }

        public String name() {
            return line.getPrintedCode() + " " + line.getDescription();
        }
    }

    public static BudgetStructure of(List<BudgetLine> linesInAnyOrder) {
        List<BudgetLine> lines = linesInAnyOrder.stream().sorted(Comparator.comparingInt(BudgetLine::getPosition)).toList();
        BudgetLine total = null;
        List<Group> groups = new ArrayList<>();
        List<BudgetLine> ungrouped = new ArrayList<>();
        Group current = null;
        for (BudgetLine l : lines) {
            switch (l.getType()) {
                case TOTAL -> total = total == null ? l : total;
                case GROUP -> {
                    current = new Group(l, new ArrayList<>(), isFunds(l));
                    groups.add(current);
                }
                case LINE -> {
                    if (current == null) {
                        ungrouped.add(l);
                    } else {
                        current.lines().add(l);
                    }
                }
            }
        }
        // Only the first funds group counts as funds, as in the rag
        List<Group> marked = new ArrayList<>();
        boolean found = false;
        for (Group g : groups) {
            boolean funds = g.funds() && !found;
            found |= funds;
            marked.add(new Group(g.line(), List.copyOf(g.lines()), funds));
        }
        return new BudgetStructure(total, List.copyOf(marked), List.copyOf(ungrouped));
    }

    public Optional<Group> funds() {
        return groups.stream().filter(Group::funds).findFirst();
    }

    public List<Group> groupsWithoutFunds() {
        return groups.stream().filter(g -> !g.funds()).toList();
    }

    /**
     * Monthly planned amount used in calculations: sum of the lines of the groups that are not funds (RF-03.1.2, Q29).
     */
    public BigDecimal monthlyPlannedFromLines() {
        return groupsWithoutFunds().stream().map(Group::linesSum).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Printed total − printed funds. Empty without a total line or without a funds group. */
    public Optional<BigDecimal> printedMonthlyPlanned() {
        if (total == null || funds().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(total.getBudgeted().subtract(funds().get().line().getBudgeted()));
    }

    public BigDecimal printedSubtotalsSum() {
        return groups.stream().map(g -> g.line().getBudgeted()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal printedSubtotalsSumWithoutFunds() {
        return groupsWithoutFunds().stream().map(g -> g.line().getBudgeted()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Funds group: the account column (or the description) starts with "Fundos", not with "Subtotal". */
    private static boolean isFunds(BudgetLine group) {
        String account = group.getAccountText() == null ? "" : normalize(group.getAccountText());
        return account.startsWith("fundos")
                || (!account.startsWith("subtotal") && normalize(group.getDescription()).startsWith("fundos"));
    }

    /**
     * Informational warnings of item numbers printed more than once (RF-13.2), never a reason to reject the confirmation:
     * "item repetido na PO: 1.3.2 (linhas 2 e 25 do grupo 1.3)". The line number is the position inside the group (1 =
     * first line after the subtotal). The lines stay separate, each with its own amounts.
     */
    public List<String> repeatedItemWarnings() {
        Map<String, List<String>> where = new LinkedHashMap<>();
        Map<String, List<String>> groupOf = new LinkedHashMap<>();
        for (Group g : groups) {
            for (int i = 0; i < g.lines().size(); i++) {
                BudgetLine l = g.lines().get(i);
                where.computeIfAbsent(l.getPrintedCode(), k -> new ArrayList<>()).add(String.valueOf(i + 1));
                groupOf.computeIfAbsent(l.getPrintedCode(), k -> new ArrayList<>()).add(g.line().getPrintedCode());
            }
        }
        for (BudgetLine l : ungrouped) {
            where.computeIfAbsent(l.getPrintedCode(), k -> new ArrayList<>()).add("ordem " + l.getPosition());
            groupOf.computeIfAbsent(l.getPrintedCode(), k -> new ArrayList<>()).add(null);
        }
        List<String> warnings = new ArrayList<>();
        where.forEach((code, numbers) -> {
            if (numbers.size() < 2) {
                return;
            }
            List<String> groupCodes = groupOf.get(code);
            boolean sameGroup = groupCodes.stream().distinct().count() == 1 && groupCodes.getFirst() != null;
            String detail;
            if (sameGroup) {
                detail = "linhas " + join(numbers) + " do grupo " + groupCodes.getFirst();
            } else {
                List<String> parts = new ArrayList<>();
                for (int i = 0; i < numbers.size(); i++) {
                    parts.add(groupCodes.get(i) == null ? numbers.get(i)
                            : "linha " + numbers.get(i) + " do grupo " + groupCodes.get(i));
                }
                detail = join(parts);
            }
            warnings.add("item repetido na PO: " + code + " (" + detail + ")");
        });
        return List.copyOf(warnings);
    }

    private static String join(List<String> parts) {
        if (parts.size() == 1) {
            return parts.getFirst();
        }
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " e " + parts.getLast();
    }

    public static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }
}
