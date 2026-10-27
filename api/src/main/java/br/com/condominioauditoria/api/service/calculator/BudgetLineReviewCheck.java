package br.com.condominioauditoria.api.service.calculator;

import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.budget.BudgetLineOrigin;
import br.com.condominioauditoria.api.model.enums.BudgetLineWarningCode;
import br.com.condominioauditoria.api.util.MoneyFormatter;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Warnings of the budget line review (RF-13.12; ADR 0009, Decision 1). <b>Pure function</b> with fixed rules, no AI:
 * what deserves attention when a person reviews the lines read from the document. Never a finding, never blocks:
 * <ul>
 * <li>{@code ACCOUNT_DESCRIPTION_MISMATCH} (O1): the printed account name and the description have no word in common
 * (words compared by their first four letters, without accents, digits and short words). Not checked in a contracts
 * group (group name with "contrat"), where the description is the supplier (RF-13.2);</li>
 * <li>{@code GROUP_ACCOUNT} (O2): the Admin marked the line as citing a group account; {@code GROUP_ACCOUNT_SUGGESTED}:
 * the account name starts with a word of a group of accounts ({@link #GROUP_ACCOUNT_WORDS}), the Admin confirms;</li>
 * <li>{@code ACCOUNT_IN_TWO_LINES}: the same account code in more than one line;</li>
 * <li>{@code REPEATED_ITEM}: the same printed item in more than one line;</li>
 * <li>{@code ZERO_BUDGETED}: budgeted amount zero;</li>
 * <li>{@code ORIGIN_FINAL_DIFFERS}: the final amount of the line's origin differs from the budget's amount by more than
 * R$ 0,01 (the budget's amount holds, O4).</li>
 * </ul>
 */
public final class BudgetLineReviewCheck {

    public static final String VERSION = "1";

    /**
     * First words of an account name that names a group of accounts, not an analytic account (O2: "Verbas Salariais"
     * in the pilot). Only a suggestion: the mark is always the Admin's.
     */
    public static final Set<String> GROUP_ACCOUNT_WORDS = Set.of("verbas", "encargos");

    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");
    private static final Pattern ACCOUNT_CODE = Pattern.compile("(?:^|\\+)\\s*(\\d{3,})\\s*-");
    private static final Pattern LEADING_CODE = Pattern.compile("(?:^|\\+)\\s*\\d{3,}\\s*-\\s*");
    private static final Set<String> STOP_WORDS = Set.of("de", "da", "do", "das", "dos", "com", "em", "para", "por",
            "e", "a", "o", "as", "os", "no", "na", "nos", "nas");

    public record Warning(BudgetLineWarningCode code, String message) {
    }

    private BudgetLineReviewCheck() {
    }

    /**
     * Warnings of every line of the budget (expense lines and the 1.9 funds), by line id, in budget order. Lines
     * without warnings have an empty list.
     */
    public static Map<UUID, List<Warning>> check(BudgetStructure structure, Map<UUID, BudgetLineOrigin> origins) {
        Map<UUID, List<Warning>> result = new LinkedHashMap<>();
        Map<String, List<BudgetLine>> byAccount = new LinkedHashMap<>();
        Map<String, List<BudgetLine>> byItem = new LinkedHashMap<>();
        List<BudgetLine> all = new ArrayList<>();
        for (BudgetStructure.Group g : structure.groups()) {
            for (BudgetLine l : g.lines()) {
                all.add(l);
                accountCodes(l.getAccount()).forEach(c -> byAccount.computeIfAbsent(c, k -> new ArrayList<>()).add(l));
                byItem.computeIfAbsent(l.getPrintedCode(), k -> new ArrayList<>()).add(l);
            }
        }
        for (BudgetStructure.Group g : structure.groups()) {
            boolean contracts = normalize(g.line().getDescription()).contains("contrat");
            for (BudgetLine l : g.lines()) {
                List<Warning> w = new ArrayList<>();
                if (!g.funds() && !contracts && l.getAccount() != null && !matches(l.getAccount(), l.getDescription())) {
                    w.add(new Warning(BudgetLineWarningCode.ACCOUNT_DESCRIPTION_MISMATCH, "Conta impressa \""
                            + l.getAccount().trim() + "\" não combina com a descrição \"" + l.getDescription().trim()
                            + "\": confira se a linha vale pela descrição (O1)"));
                }
                if (l.isGroupAccount()) {
                    w.add(new Warning(BudgetLineWarningCode.GROUP_ACCOUNT, "Linha marcada com conta de grupo: na"
                            + " sugestão do de-para, a linha com conta própria ganha desta (O2)"));
                } else if (!g.funds() && looksLikeGroup(l.getAccount())) {
                    w.add(new Warning(BudgetLineWarningCode.GROUP_ACCOUNT_SUGGESTED, "\"" + accountName(l.getAccount())
                            + "\" parece uma conta de grupo, que reúne outras contas: confira e marque (O2)"));
                }
                for (String code : accountCodes(l.getAccount())) {
                    List<BudgetLine> same = byAccount.get(code);
                    if (same.size() > 1) {
                        w.add(new Warning(BudgetLineWarningCode.ACCOUNT_IN_TWO_LINES, "Conta " + code + " em "
                                + same.size() + " linhas: " + codes(same) + " (a separação é pelo fornecedor ou pela"
                                + " descrição, RF-13.8)"));
                    }
                }
                List<BudgetLine> sameItem = byItem.get(l.getPrintedCode());
                if (sameItem.size() > 1) {
                    w.add(new Warning(BudgetLineWarningCode.REPEATED_ITEM, "Item repetido na PO: " + l.getPrintedCode()
                            + " (ordens " + sameItem.stream().map(x -> String.valueOf(x.getPosition()))
                                    .collect(Collectors.joining(" e ")) + ")"));
                }
                if (!g.funds() && l.getBudgeted() != null && l.getBudgeted().signum() == 0) {
                    w.add(new Warning(BudgetLineWarningCode.ZERO_BUDGETED, "Orçado zero: qualquer gasto nesta linha"
                            + " aparece como estouro"));
                }
                BudgetLineOrigin o = origins == null ? null : origins.get(l.getId());
                if (o != null && o.getFinalAmount() != null && l.getBudgeted() != null
                        && o.getFinalAmount().subtract(l.getBudgeted()).abs().compareTo(TOLERANCE) > 0) {
                    w.add(new Warning(BudgetLineWarningCode.ORIGIN_FINAL_DIFFERS, "Valor final da origem R$ "
                            + MoneyFormatter.format(o.getFinalAmount()) + " difere do valor da PO R$ "
                            + MoneyFormatter.format(l.getBudgeted()) + ": vale o da PO"));
                }
                result.put(l.getId(), List.copyOf(w));
            }
        }
        return result;
    }

    /** The account codes cited in the account column ("1591 - Telefone Fixo + 1592 - Telefone Móvel" → 1591, 1592). */
    public static List<String> accountCodes(String account) {
        if (account == null) {
            return List.of();
        }
        List<String> codes = new ArrayList<>();
        Matcher m = ACCOUNT_CODE.matcher(account);
        while (m.find()) {
            codes.add(m.group(1));
        }
        return List.copyOf(codes);
    }

    /** O1: true when the account name and the description share a word (by its first four letters). */
    public static boolean matches(String account, String description) {
        Set<String> a = stems(accountName(account));
        if (a.isEmpty()) {
            return true;
        }
        Set<String> d = stems(description);
        return d.stream().anyMatch(a::contains);
    }

    static boolean looksLikeGroup(String account) {
        List<String> words = words(accountName(account));
        return !words.isEmpty() && GROUP_ACCOUNT_WORDS.contains(words.getFirst());
    }

    static String accountName(String account) {
        return account == null ? "" : LEADING_CODE.matcher(account).replaceAll(" ").trim();
    }

    private static Set<String> stems(String text) {
        Set<String> s = new LinkedHashSet<>();
        for (String w : words(text)) {
            if (w.length() >= 3) {
                s.add(w.length() > 4 ? w.substring(0, 4) : w);
            }
        }
        return s;
    }

    private static List<String> words(String text) {
        String n = normalize(text).replaceAll("[^a-z]+", " ").trim();
        if (n.isEmpty()) {
            return List.of();
        }
        List<String> w = new ArrayList<>();
        for (String p : n.split(" ")) {
            if (!STOP_WORDS.contains(p)) {
                w.add(p);
            }
        }
        return w;
    }

    private static String codes(List<BudgetLine> lines) {
        return lines.stream().map(BudgetLine::getEffectiveCode).collect(Collectors.joining(" e "));
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
