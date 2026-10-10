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
 * suggestion when the score reaches the minimum and no other line ties with it. The suggestion is never confirmed here.
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
        List<String> cashFlow = normalize(cashFlowName);
        if (cashFlow.isEmpty()) {
            return new NoSuggestion("nome da conta do fluxo sem palavras para comparar");
        }
        Score best = Score.ZERO;
        List<BudgetLine> tied = new ArrayList<>();
        List<String> bestName = List.of();
        for (BudgetLine line : candidates) {
            List<String> account = normalize(line.getAccount());
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
        if (tied.isEmpty()) {
            return new NoSuggestion("fluxo: " + cashFlowText + "; nenhuma linha da PO com palavras em comum");
        }
        if (!best.reaches(minScore)) {
            return new NoSuggestion("fluxo: " + cashFlowText + "; melhor linha " + tied.getFirst().getEffectiveCode()
                    + " com nota " + display(best.value()) + ", abaixo do mínimo " + display(minScore));
        }
        if (tied.size() > 1) {
            return new NoSuggestion("fluxo: " + cashFlowText + "; empate entre as linhas " + tied.stream()
                    .map(BudgetLine::getEffectiveCode).collect(Collectors.joining(", ")) + " (nota "
                    + display(best.value()) + "): escolha na lista");
        }
        BudgetLine chosen = tied.getFirst();
        Set<String> common = new LinkedHashSet<>(cashFlow);
        common.retainAll(bestName);
        return new Suggested(chosen, best, "fluxo: " + cashFlowText + "; PO " + chosen.getEffectiveCode() + ": "
                + String.join(" ", bestName) + " (em comum: " + String.join(", ", common) + "; nota "
                + display(best.value()) + ")");
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
