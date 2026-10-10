package br.com.condominioauditoria.api.service.calculator;

import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.enums.BudgetItemSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Suggestion of the budget item of each line of a budget (RF-11.7; ADR 0005, Decision 1). Pure function, without AI.
 *
 * <p>Rules, in this order:
 * <ol>
 * <li>Two or more lines of this budget with the same account in the same group: no suggestion (adding them up needs
 * the Admin).</li>
 * <li>Same line (same effective code and same account) confirmed in the previous version of the same fiscal year: its
 * item, source {@code PREVIOUS_VERSION}.</li>
 * <li>Confirmed lines of other budgets with the same budget account and the same group: if all are in a single item,
 * that one, source {@code BUDGET_ACCOUNT}; if they are in more than one, or there is none, no suggestion.</li>
 * </ol>
 * Never uses the item code (it changes between budgets). The description, usually the supplier, only counts when the
 * line has neither an account nor text in the account column.
 */
public final class BudgetItemSuggestion {

    private BudgetItemSuggestion() {
    }

    /** Expense or fund line with the code of its group (null for a line outside any group). */
    public record LineWithGroup(BudgetLine line, String group) {

        /** What is compared: the budget's account, otherwise the account column text, otherwise the description. */
        public String label() {
            return labelOf(line);
        }

        public String key() {
            return BudgetStructure.normalize(label()).replaceAll("\\s+", " ");
        }

        public String keyWithGroup() {
            return key() + "|" + (group == null ? "" : group);
        }
    }

    /** Line of another budget with a confirmed item. */
    public record Confirmed(LineWithGroup line, UUID budgetItemId) {
    }

    public sealed interface Result {
        String reason();
    }

    public record Suggested(UUID budgetItemId, BudgetItemSource source, String reason) implements Result {
    }

    public record NoSuggestion(String reason) implements Result {
    }

    /** Lines that receive an item: every LINE-type line, expense and fund, in budget order. */
    public static List<LineWithGroup> lines(BudgetStructure structure) {
        List<LineWithGroup> all = new ArrayList<>();
        structure.ungrouped().forEach(l -> all.add(new LineWithGroup(l, null)));
        for (BudgetStructure.Group g : structure.groups()) {
            g.lines().forEach(l -> all.add(new LineWithGroup(l, g.line().getEffectiveCode())));
        }
        return all;
    }

    public static String labelOf(BudgetLine l) {
        if (hasText(l.getAccount())) {
            return l.getAccount().trim();
        }
        if (hasText(l.getAccountText())) {
            return l.getAccountText().trim();
        }
        return l.getDescription() == null ? "" : l.getDescription().trim();
    }

    /**
     * @param newLines lines to suggest (the ones without an item in this budget yet)
     *
     * @param allOfBudget every line of this budget (to find accounts repeated in the same group)
     *
     * @param previousVersion confirmed lines of the previous version of the same fiscal year (empty when there is
     *     none)
     *
     * @param otherBudgets confirmed lines of every other budget of the condominium
     *
     * @return result by line id, in the order of {@code newLines}
     */
    public static Map<UUID, Result> suggest(List<LineWithGroup> newLines, List<LineWithGroup> allOfBudget,
            List<Confirmed> previousVersion, List<Confirmed> otherBudgets) {
        Map<String, Integer> repeated = new HashMap<>();
        allOfBudget.forEach(l -> repeated.merge(l.keyWithGroup(), 1, Integer::sum));

        Map<String, UUID> fromPrevious = new HashMap<>();
        for (Confirmed c : previousVersion) {
            fromPrevious.put(c.line().line().getEffectiveCode() + "|" + c.line().key(), c.budgetItemId());
        }
        Map<String, Set<UUID>> byAccountAndGroup = new HashMap<>();
        for (Confirmed c : otherBudgets) {
            byAccountAndGroup.computeIfAbsent(c.line().keyWithGroup(),
                    k -> new LinkedHashSet<>()).add(c.budgetItemId());
        }

        Map<UUID, Result> result = new LinkedHashMap<>();
        for (LineWithGroup l : newLines) {
            result.put(l.line().getId(), suggest(l, repeated, fromPrevious, byAccountAndGroup));
        }
        return result;
    }

    private static Result suggest(LineWithGroup l, Map<String, Integer> repeated, Map<String, UUID> fromPrevious,
            Map<String, Set<UUID>> byAccountAndGroup) {
        String group = l.group() == null ? "sem grupo" : l.group();
        String what = fieldDescription(l.line());
        if (l.key().isEmpty()) {
            return new NoSuggestion("linha sem conta da PO nem descrição: escolha a rubrica na lista");
        }
        if (repeated.getOrDefault(l.keyWithGroup(), 0) > 1) {
            return new NoSuggestion(what + " \"" + l.label() + "\" aparece em mais de uma linha do grupo " + group
                    + " nesta PO: escolha a rubrica de cada linha");
        }
        UUID previous = fromPrevious.get(l.line().getEffectiveCode() + "|" + l.key());
        if (previous != null) {
            return new Suggested(previous, BudgetItemSource.PREVIOUS_VERSION, "linha igual na versão anterior: "
                    + l.line().getEffectiveCode() + " " + l.label());
        }
        Set<UUID> items = byAccountAndGroup.getOrDefault(l.keyWithGroup(), Set.of());
        if (items.size() == 1) {
            return new Suggested(items.iterator().next(), BudgetItemSource.BUDGET_ACCOUNT,
                    sameField(l.line()) + " e mesmo grupo: " + l.label() + ", " + group);
        }
        if (items.size() > 1) {
            return new NoSuggestion(what + " \"" + l.label() + "\" no grupo " + group + " está em " + items.size()
                    + " rubricas: escolha na lista");
        }
        return new NoSuggestion("nenhuma linha confirmada com " + what + " \"" + l.label() + "\" no grupo " + group
                + ": escolha na lista ou crie uma rubrica");
    }

    /** "a conta da PO", "o texto da conta" or "a descrição", depending on the compared field. */
    private static String fieldDescription(BudgetLine l) {
        if (hasText(l.getAccount())) {
            return "a conta da PO";
        }
        if (hasText(l.getAccountText())) {
            return "o texto da conta";
        }
        return "a descrição";
    }

    /** "mesma conta da PO", "mesmo texto da conta" or "mesma descrição" (start of the suggestion's reason). */
    private static String sameField(BudgetLine l) {
        if (hasText(l.getAccount())) {
            return "mesma conta da PO";
        }
        if (hasText(l.getAccountText())) {
            return "mesmo texto da conta";
        }
        return "mesma descrição";
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
