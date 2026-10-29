package br.com.condominioauditoria.api.service.calculator;

import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.budget.BudgetSheetAccount;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Account mapping through the budget's support sheet (Q54; ADR 0009, Decision 8). <b>Pure function</b>, without AI.
 *
 * <p>The cash flow and the budget use different charts of accounts (RF-00.8), so the number is never compared. The
 * bridge is the <b>name</b>: the expenses sheet of the support sheet lists the budget's accounts with their names and the
 * group of each one (deduced by the rag from the sums), and the origin of each budget line cites the accounts its
 * amount came from. So:
 * <ol>
 * <li>the cash flow name, without accents, punctuation and stop words, with the abbreviations expanded and keeping the
 * numbers, must be <b>equal</b> to the name of exactly one account of the sheet ("ADIANTAMENTO QUINZENAL" = 1547
 * Adiantamento Quinzenal);</li>
 * <li>the line is the only one whose origin cites that account; if none does, its group (1545 Verbas Salariais → 1.1.1),
 * then the group's group. A line whose printed account does not match its description (O1, as in
 * {@link BudgetLineReviewCheck}, or marked "vale a descrição") is not reached by its account: "Salário" (1546, printed in
 * 1.1.2 "INSS/FGTS/PIS/IRPF") goes up to 1545 and to 1.1.1;</li>
 * <li>an account cited by more than one line (1624 in 1.3.25 and 1.8.8) is decided among those lines by the
 * {@link NameSuggestion} rules (name, supplier, description); without a decision, no link;</li>
 * <li><b>low confidence</b>: no line cites the account nor its groups, but exactly one line cites a sibling account
 * (same group in the sheet): "ASSISTÊNCIA FUNERAL" (1738, group 1736 Seguros Funcionários) → 1.1.9, whose origin is
 * 1737 Seguro de Vida.</li>
 * </ol>
 */
public final class BudgetSheetBridge {

    /** A link found through the sheet; {@code lowConfidence} for the sibling rule. */
    public record Link(BudgetLine line, String reason, boolean lowConfidence) {
    }

    private final NameSuggestion names;
    private final Map<String, BudgetSheetAccount> byCode = new LinkedHashMap<>();
    private final Map<Set<String>, List<BudgetSheetAccount>> byName = new LinkedHashMap<>();
    private final List<BudgetLine> candidates;
    private final Map<UUID, List<String>> sourceAccounts;
    private final Set<UUID> notByAccount;

    /**
     * @param accounts the accounts of the expenses sheet of this budget (empty: the bridge never links)
     * @param candidates the lines that may receive a debit ({@link NameSuggestion#candidates})
     * @param sourceAccounts line id → the accounts its origin cites
     * @param notByAccount lines reached only by their description (O1)
     */
    public BudgetSheetBridge(NameSuggestion names, Collection<BudgetSheetAccount> accounts, List<BudgetLine> candidates,
            Map<UUID, List<String>> sourceAccounts, Set<UUID> notByAccount) {
        this.names = names;
        for (BudgetSheetAccount a : accounts) {
            byCode.putIfAbsent(a.getAccountCode(), a);
            Set<String> key = Set.copyOf(names.normalizeKeepingNumbers(a.getAccountName()));
            if (!key.isEmpty()) {
                byName.computeIfAbsent(key, k -> new ArrayList<>()).add(a);
            }
        }
        this.candidates = List.copyOf(candidates);
        this.sourceAccounts = Map.copyOf(sourceAccounts);
        this.notByAccount = Set.copyOf(notByAccount);
    }

    public boolean isEmpty() {
        return byCode.isEmpty();
    }

    public Optional<Link> link(String cashFlowName, Collection<String> suppliers, NameSuggestion.LineHints hints) {
        Set<String> key = Set.copyOf(names.normalizeKeepingNumbers(cashFlowName));
        List<BudgetSheetAccount> same = key.isEmpty() ? List.of() : byName.getOrDefault(key, List.of());
        if (same.size() != 1) {
            return Optional.empty();
        }
        BudgetSheetAccount account = same.getFirst();
        String start = account.getAccountCode() + " " + account.getAccountName();
        Set<String> seen = new HashSet<>();
        String code = account.getAccountCode();
        List<String> path = new ArrayList<>();
        while (code != null && seen.add(code)) {
            BudgetSheetAccount current = byCode.get(code);
            path.add(current == null ? code : code + " " + current.getAccountName());
            List<BudgetLine> lines = linesCiting(code);
            String via = "planilha de apoio da PO: conta " + String.join(" → grupo ", path);
            if (lines.size() == 1) {
                BudgetLine l = lines.getFirst();
                return Optional.of(new Link(l, via + ", origem da linha " + l.getEffectiveCode(), false));
            }
            if (lines.size() > 1) {
                var r = names.suggest(cashFlowName, suppliers, lines, hints);
                // Among the lines that cite this same account, the tie broken by the description is a sure link
                if (r instanceof NameSuggestion.Suggested s) {
                    return Optional.of(new Link(s.line(), via + ", origem das linhas " + codes(lines) + "; "
                            + s.reason().replaceFirst("^" + NameSuggestion.LOW_CONFIDENCE, ""), false));
                }
                return Optional.empty();
            }
            code = current == null ? null : current.getParentCode();
        }
        String parent = account.getParentCode();
        if (parent == null) {
            return Optional.empty();
        }
        Set<BudgetLine> bySibling = new LinkedHashSet<>();
        List<String> siblings = new ArrayList<>();
        for (BudgetSheetAccount sibling : byCode.values()) {
            if (parent.equals(sibling.getParentCode()) && !sibling.getAccountCode().equals(account.getAccountCode())) {
                List<BudgetLine> lines = linesCiting(sibling.getAccountCode());
                if (!lines.isEmpty()) {
                    siblings.add(sibling.getAccountCode() + " " + sibling.getAccountName());
                }
                bySibling.addAll(lines);
            }
        }
        if (bySibling.size() != 1) {
            return Optional.empty();
        }
        BudgetLine l = bySibling.iterator().next();
        return Optional.of(new Link(l, NameSuggestion.LOW_CONFIDENCE + "planilha de apoio da PO: conta " + start
                + " sem linha própria; a conta do mesmo grupo " + parent + " (" + String.join(", ", siblings)
                + ") é origem da linha " + l.getEffectiveCode(), true));
    }

    /** Candidate lines whose origin cites the account, without the lines reached only by their description (O1). */
    private List<BudgetLine> linesCiting(String code) {
        return candidates.stream().filter(l -> !notByAccount.contains(l.getId()))
                .filter(l -> sourceAccounts.getOrDefault(l.getId(), List.of()).contains(code)).toList();
    }

    private static String codes(List<BudgetLine> lines) {
        return lines.stream().map(BudgetLine::getEffectiveCode).collect(Collectors.joining(", "));
    }
}
