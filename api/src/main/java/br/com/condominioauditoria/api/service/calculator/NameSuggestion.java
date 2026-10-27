package br.com.condominioauditoria.api.service.calculator;

import br.com.condominioauditoria.api.config.properties.AccountMappingProperties;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.enums.BudgetLineMark;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Suggestion of a cash flow account's target by name (RF-03.1.5; ADR 0004, Decision 4). Deterministic text comparison,
 * without AI, database or clock: works in any AI mode, OFF included.
 *
 * <p>The account number never matters (RF-03.1.4): normalization drops every digit, on both sides. So cash flow account
 * 1621 ("MATERIAL HIDRÁULICO") has nothing in common with budget line 1.3.23 ("1621 - Interfones").
 *
 * <p>Score = common words ÷ distinct words of both names (Jaccard), compared as an exact fraction. The cash flow name
 * is compared with the budget's account and with the line description; the higher one counts. The best line becomes a
 * suggestion when the score reaches the minimum and no other line ties with it. Two more rules (Q51), both explained
 * in the reason and both blind to the account number:
 * <ul>
 * <li><b>tie broken by the supplier</b> (RF-13.8): among the tied lines, the only one whose description is entirely
 * inside the name of a supplier of the account's entries ("Rio Gelar" in "RIO GELAR REFRIGERACAO LTDA");</li>
 * <li><b>every cash flow word is in the line</b>: when the score does not reach the minimum, the only line whose
 * budget account plus description contain all the cash flow words ("INSS" in "Salário · INSS/FGTS/PIS/IRPF").</li>
 * </ul>
 * Two rules of the line review (O1 and O2; ADR 0009, Decision 3), from the Admin's marks ({@link LineHints}):
 * <ul>
 * <li><b>description prevails</b> (O1): a line marked "vale a descrição" is compared only by its description, never by
 * the printed account;</li>
 * <li><b>analytic account beats the group account</b> (O2): a line marked "conta de grupo" is compared only by its
 * description, and in a tie the other lines win over it (holidays go to 1.1.5 and the 13th salary to 1.1.12, not to
 * 1.1.1).</li>
 * </ul>
 * The suggestion is never confirmed here.
 */
public final class NameSuggestion {

    /** Exact fraction (no floating point). */
    public record Score(int common, int total) implements Comparable<Score> {

        static final Score ZERO = new Score(0, 1);

        @Override
        public int compareTo(Score o) {
            return Long.compare((long) common * o.total, (long) o.common * total);
        }

        public BigDecimal value() {
            return BigDecimal.valueOf(common).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        }

        boolean reaches(BigDecimal minimum) {
            return BigDecimal.valueOf(common).compareTo(minimum.multiply(BigDecimal.valueOf(total))) >= 0;
        }
    }

    /** The Admin's marks of the line review used here (O1, O2); empty = no mark. */
    public record LineHints(Set<java.util.UUID> groupAccount, Set<java.util.UUID> descriptionPrevails) {

        public static final LineHints NONE = new LineHints(Set.of(), Set.of());

        public LineHints {
            groupAccount = groupAccount == null ? Set.of() : Set.copyOf(groupAccount);
            descriptionPrevails = descriptionPrevails == null ? Set.of() : Set.copyOf(descriptionPrevails);
        }

        public static LineHints of(List<BudgetLine> lines) {
            return new LineHints(lines.stream().filter(BudgetLine::isGroupAccount).map(BudgetLine::getId)
                    .collect(Collectors.toSet()), lines.stream().filter(BudgetLine::isDescriptionPrevails)
                    .map(BudgetLine::getId).collect(Collectors.toSet()));
        }

        boolean byDescriptionOnly(BudgetLine line) {
            return groupAccount.contains(line.getId()) || descriptionPrevails.contains(line.getId());
        }
    }

    public sealed interface Result permits Suggested, NoSuggestion {
        String reason();
    }

    public record Suggested(BudgetLine line, Score score, String reason) implements Result {
    }

    public record NoSuggestion(String reason) implements Result {
    }

    private final BigDecimal minScore;
    private final Set<String> stopWords;
    private final java.util.Map<String, String> abbreviations;

    public NameSuggestion(AccountMappingProperties properties) {
        this.minScore = properties.minScore();
        this.stopWords = Set.copyOf(properties.stopWords());
        this.abbreviations = properties.abbreviations();
    }

    /**
     * Lines that can receive a debit from the Condomínio fund: expense lines (without the 1.9 funds) and without
     * "rateio à parte".
     */
    public static List<BudgetLine> candidates(BudgetStructure structure) {
        return structure.groupsWithoutFunds().stream().flatMap(g -> g.lines().stream())
                .filter(l -> l.getMark() != BudgetLineMark.SEPARATE_APPORTIONMENT).toList();
    }

    /** Upper case, without accents, punctuation or digits; abbreviations expanded and stop words removed. */
    public List<String> normalize(String text) {
        if (text == null) {
            return List.of();
        }
        String withoutAccents = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT);
        // Everything that is not a letter (digits, punctuation, symbols) becomes a separator
        String lettersOnly = withoutAccents.replaceAll("[^A-Z]+", " ").trim();
        if (lettersOnly.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> words = new LinkedHashSet<>();
        for (String p : lettersOnly.split(" ")) {
            String whole = abbreviations.getOrDefault(p, p);
            if (!stopWords.contains(whole)) {
                words.add(whole);
            }
        }
        return List.copyOf(words);
    }

    public Result suggest(String cashFlowName, List<BudgetLine> candidates) {
        return suggest(cashFlowName, List.of(), candidates);
    }

    /**
     * @param suppliers names of the suppliers printed in the account's entries (only used to break a tie)
     */
    public Result suggest(String cashFlowName, java.util.Collection<String> suppliers, List<BudgetLine> candidates) {
        return suggest(cashFlowName, suppliers, candidates, LineHints.NONE);
    }

    /** @param hints the Admin's marks of the line review (O1, O2) */
    public Result suggest(String cashFlowName, java.util.Collection<String> suppliers, List<BudgetLine> candidates,
            LineHints hints) {
        List<String> cashFlow = normalize(cashFlowName);
        if (cashFlow.isEmpty()) {
            return new NoSuggestion("nome da conta do fluxo sem palavras para comparar");
        }
        Score best = Score.ZERO;
        List<BudgetLine> tied = new ArrayList<>();
        List<String> bestName = List.of();
        for (BudgetLine line : candidates) {
            List<String> account = hints.byDescriptionOnly(line) ? List.of() : normalize(line.getAccount());
            List<String> description = normalize(line.getDescription());
            Score byAccount = score(cashFlow, account);
            Score byDescription = score(cashFlow, description);
            boolean winningAccount = byAccount.compareTo(byDescription) >= 0;
            Score n = winningAccount ? byAccount : byDescription;
            int c = n.compareTo(best);
            if (c > 0) {
                best = n;
                tied.clear();
                tied.add(line);
                bestName = winningAccount ? account : description;
            } else if (c == 0 && n.common() > 0) {
                tied.add(line);
            }
        }
        String cashFlowText = String.join(" ", cashFlow);
        if (tied.isEmpty() || !best.reaches(minScore)) {
            Optional<Suggested> contained = allWordsInOneLine(cashFlow, cashFlowText, candidates, hints);
            if (contained.isPresent()) {
                return contained.get();
            }
            return tied.isEmpty()
                    ? new NoSuggestion("fluxo: " + cashFlowText + "; nenhuma linha da PO com palavras em comum")
                    : new NoSuggestion("fluxo: " + cashFlowText + "; melhor linha " + tied.getFirst().getEffectiveCode()
                            + " com nota " + display(best.value()) + ", abaixo do mínimo " + display(minScore));
        }
        String groupNote = "";
        if (tied.size() > 1 && tied.stream().anyMatch(l -> hints.groupAccount().contains(l.getId()))
                && tied.stream().anyMatch(l -> !hints.groupAccount().contains(l.getId()))) {
            // O2: the line with its own account wins over the line marked as citing a group account
            String grouped = tied.stream().filter(l -> hints.groupAccount().contains(l.getId()))
                    .map(BudgetLine::getEffectiveCode).collect(Collectors.joining(", "));
            tied.removeIf(l -> hints.groupAccount().contains(l.getId()));
            groupNote = "; a linha com conta própria ganha da linha com conta de grupo " + grouped + " (O2)";
        }
        if (tied.size() > 1) {
            String tiedCodes = tied.stream().map(BudgetLine::getEffectiveCode).collect(Collectors.joining(", "));
            Optional<Suggested> bySupplier = tieBrokenBySupplier(tied, suppliers, best, cashFlowText, tiedCodes);
            if (bySupplier.isPresent()) {
                return bySupplier.get();
            }
            return new NoSuggestion("fluxo: " + cashFlowText + "; empate entre as linhas " + tiedCodes + " (nota "
                    + display(best.value()) + "): escolha na lista");
        }
        BudgetLine chosen = tied.getFirst();
        if (!groupNote.isEmpty()) {
            List<String> account = hints.byDescriptionOnly(chosen) ? List.of() : normalize(chosen.getAccount());
            List<String> description = normalize(chosen.getDescription());
            bestName = score(cashFlow, account).compareTo(score(cashFlow, description)) >= 0 ? account : description;
        }
        Set<String> common = new LinkedHashSet<>(cashFlow);
        common.retainAll(bestName);
        return new Suggested(chosen, best, "fluxo: " + cashFlowText + "; PO " + chosen.getEffectiveCode() + ": "
                + String.join(" ", bestName) + " (em comum: " + String.join(", ", common) + "; nota "
                + display(best.value()) + ")" + groupNote);
    }

    /**
     * The only tied line whose description (with at least one word) is entirely inside the name of one of the
     * suppliers: the contract line of that supplier (RF-13.8). None or more than one: no suggestion.
     */
    private Optional<Suggested> tieBrokenBySupplier(List<BudgetLine> tied, java.util.Collection<String> suppliers,
            Score best, String cashFlowText, String tiedCodes) {
        List<String> sortedSuppliers = suppliers.stream().filter(java.util.Objects::nonNull).distinct().sorted().toList();
        BudgetLine chosen = null;
        String supplierFound = null;
        for (BudgetLine line : tied) {
            List<String> description = normalize(line.getDescription());
            if (description.isEmpty()) {
                continue;
            }
            Optional<String> supplier = sortedSuppliers.stream()
                    .filter(f -> Set.copyOf(normalize(f)).containsAll(description)).findFirst();
            if (supplier.isPresent()) {
                if (chosen != null) {
                    return Optional.empty();
                }
                chosen = line;
                supplierFound = supplier.get();
            }
        }
        if (chosen == null) {
            return Optional.empty();
        }
        return Optional.of(new Suggested(chosen, best, "fluxo: " + cashFlowText + "; empate entre as linhas "
                + tiedCodes + " (nota " + display(best.value()) + "); desempate pelo favorecido: \"" + supplierFound
                + "\" contém a descrição da linha " + chosen.getEffectiveCode() + " (" + chosen.getDescription() + ")"));
    }

    /** The only line whose budget account plus description contain every cash flow word. None or more: empty. */
    private Optional<Suggested> allWordsInOneLine(List<String> cashFlow, String cashFlowText,
            List<BudgetLine> candidates, LineHints hints) {
        BudgetLine chosen = null;
        List<String> chosenWords = List.of();
        for (BudgetLine line : candidates) {
            LinkedHashSet<String> words = new LinkedHashSet<>(hints.byDescriptionOnly(line) ? List.of()
                    : normalize(line.getAccount()));
            words.addAll(normalize(line.getDescription()));
            if (words.containsAll(cashFlow)) {
                if (chosen != null) {
                    return Optional.empty();
                }
                chosen = line;
                chosenWords = List.copyOf(words);
            }
        }
        if (chosen == null) {
            return Optional.empty();
        }
        return Optional.of(new Suggested(chosen, score(cashFlow, chosenWords), "fluxo: " + cashFlowText + "; PO "
                + chosen.getEffectiveCode() + ": " + String.join(" ", chosenWords)
                + " (todas as palavras do fluxo estão na linha, e só nela: " + String.join(", ", cashFlow) + ")"));
    }

    private static Score score(List<String> a, List<String> b) {
        if (b.isEmpty()) {
            return Score.ZERO;
        }
        Set<String> union = new LinkedHashSet<>(a);
        union.addAll(b);
        Set<String> common = new LinkedHashSet<>(a);
        common.retainAll(b);
        return new Score(common.size(), union.size());
    }

    private static String display(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }
}
