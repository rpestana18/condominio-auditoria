package br.com.condominioauditoria.api.service.calculator;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.api.config.properties.AccountMappingProperties;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.enums.BudgetLineMark;
import br.com.condominioauditoria.api.model.enums.BudgetLineType;
import br.com.condominioauditoria.api.service.budget.PilotBudget;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** RF-03.1.4 and RF-03.1.5: suggestion by name, without AI and never using the account number. */
class NameSuggestionTest {

    private final NameSuggestion suggestion = new NameSuggestion(AccountMappingProperties.defaults());
    private final List<BudgetLine> lines = PilotBudget.defaults().savedLines(
            new Budget(UUID.randomUUID(), UUID.randomUUID(), "a".repeat(64)));
    private final List<BudgetLine> candidates = NameSuggestion.candidates(BudgetStructure.of(lines));

    @Test
    void account1621PlumbingMaterialGoesTo178AndNeverTo1323() {
        var r = suggestion.suggest("MATERIAL HIDRÁULICO", candidates);

        assertThat(r).isInstanceOf(NameSuggestion.Suggested.class);
        var s = (NameSuggestion.Suggested) r;
        assertThat(s.line().getEffectiveCode()).isEqualTo("1.7.8");
        assertThat(s.reason()).contains("fluxo: MATERIAL HIDRAULICO").contains("PO 1.7.8").doesNotContain("1621");
    }

    @Test
    void accountNumberNeverMatches() {
        // Even with the number 1621 in the cash flow name, line 1.3.23 ("1621 - Interfones") gains nothing
        assertThat(suggestion.suggest("1621", candidates)).isInstanceOf(NameSuggestion.NoSuggestion.class);
        var r = suggestion.suggest("1621 MATERIAL HIDRAULICO", candidates);
        assertThat(((NameSuggestion.Suggested) r).line().getEffectiveCode()).isEqualTo("1.7.8");
        assertThat(suggestion.normalize("1621 - Interfones")).containsExactly("INTERFONES");
        for (BudgetLine l : candidates) {
            assertThat(String.join(" ", suggestion.normalize(l.getAccount()))).doesNotContainPattern("[0-9]");
        }
    }

    @Test
    void normalizesAccentPunctuationAbbreviationAndStopWord() {
        assertThat(suggestion.normalize("Desp. c/ Mat. de Construção 13°")).containsExactly("DESPESA", "MATERIAL",
                "CONSTRUCAO");
        assertThat(suggestion.normalize(null)).isEmpty();
    }

    @Test
    void tieDoesNotSuggest() {
        // "Caixa D'água" is in the account of the second 1.3.2 and in no other line: suggests; two equal ones: tie
        var pair = new java.util.ArrayList<>(candidates);
        pair.add(extraLine("1.8.8", "1624 - Caixa D'água", "Limpeza caixa d'água"));
        var r = suggestion.suggest("CAIXA D'ÁGUA", pair);

        assertThat(r).isInstanceOf(NameSuggestion.NoSuggestion.class);
        assertThat(r.reason()).contains("empate").contains("1.3.2").contains("1.8.8");
    }

    @Test
    void scoreBelowMinimumDoesNotSuggest() {
        var r = suggestion.suggest("OUTRAS DESPESAS C/PESSOAL DIVERSO", candidates);

        assertThat(r).isInstanceOf(NameSuggestion.NoSuggestion.class);
    }

    @Test
    void separateApportionmentAndFundsAreNotCandidates() {
        assertThat(candidates).noneMatch(l -> l.getMark() == BudgetLineMark.SEPARATE_APPORTIONMENT);
        assertThat(candidates).noneMatch(l -> l.getEffectiveCode().startsWith("1.9"));
        assertThat(suggestion.suggest("FUNDO DE RESERVA", candidates)).isInstanceOf(NameSuggestion.NoSuggestion.class);
    }

    @Test
    void sameInputSameSuggestion() {
        var a = suggestion.suggest("MATERIAL HIDRÁULICO", candidates);
        var b = new NameSuggestion(AccountMappingProperties.defaults()).suggest("MATERIAL HIDRÁULICO", candidates);
        assertThat(a.reason()).isEqualTo(b.reason());
    }

    @Test
    void tieIsBrokenOnlyWhenTheLineDescriptionIsTheSupplier() {
        // Q51 / RF-13.8: "AR CONDICIONADO" ties the contract line (description = supplier) and the parts line
        List<BudgetLine> pair = List.of(extraLine("1.3.4", "1600 - Ar Condicionado", "Rio Gelar"),
                extraLine("1.7.10", "1600 - Ar Condicionado", "Peças e Aquisições / Ar Cond"));

        var r = suggestion.suggest("AR CONDICIONADO", List.of("RIO GELAR REFRIGERACAO LTDA"), pair);

        assertThat(((NameSuggestion.Suggested) r).line().getEffectiveCode()).isEqualTo("1.3.4");
        assertThat(r.reason()).contains("empate entre as linhas 1.3.4, 1.7.10").contains("desempate pelo favorecido")
                .contains("RIO GELAR REFRIGERACAO LTDA");
        // Without the supplier, or with a supplier that names no line, it stays a tie
        assertThat(suggestion.suggest("AR CONDICIONADO", pair)).isInstanceOf(NameSuggestion.NoSuggestion.class);
        assertThat(suggestion.suggest("AR CONDICIONADO", List.of("OUTRA EMPRESA LTDA"), pair))
                .isInstanceOf(NameSuggestion.NoSuggestion.class);
        // Two tied lines that both fit the supplier: no suggestion
        List<BudgetLine> both = List.of(extraLine("1.3.4", "1600 - Ar Condicionado", "Rio Gelar"),
                extraLine("1.7.10", "1600 - Ar Condicionado", "Gelar"));
        assertThat(suggestion.suggest("AR CONDICIONADO", List.of("RIO GELAR REFRIGERACAO LTDA"), both))
                .isInstanceOf(NameSuggestion.NoSuggestion.class);
    }

    @Test
    void everyCashFlowWordInOnlyOneLineSuggests() {
        // Q51: "INSS" scores 1/4 against "1546 - Salário · INSS/FGTS/PIS/IRPF" (below 0.5), but is entirely in it
        List<BudgetLine> list = List.of(extraLine("1.1.1", "1545 - Verbas Salariais", "Salários Empregados"),
                extraLine("1.1.2", "1546 - Salário", "INSS/FGTS/PIS/IRPF"),
                extraLine("1.2.1", "1591 - Telefone Fixo", "Tefone móvel/fixo"));

        var inss = suggestion.suggest("INSS", list);
        assertThat(((NameSuggestion.Suggested) inss).line().getEffectiveCode()).isEqualTo("1.1.2");
        assertThat(inss.reason()).contains("todas as palavras do fluxo estão na linha");
        // Account plus description together: TELEFONE (account) and MOVEL (description)
        assertThat(((NameSuggestion.Suggested) suggestion.suggest("TELEFONE MÓVEL", list)).line().getEffectiveCode())
                .isEqualTo("1.2.1");
        // A word in more than one line, or a word in none: no suggestion
        var twice = new java.util.ArrayList<>(list);
        twice.add(extraLine("1.6.19", "1753 - Obrigações", "INSS sobre autônomos e ESOCIAL"));
        assertThat(suggestion.suggest("INSS", twice)).isInstanceOf(NameSuggestion.NoSuggestion.class);
        assertThat(suggestion.suggest("ESOCIAL INSS PATRONAL", twice)).isInstanceOf(NameSuggestion.NoSuggestion.class);
        // The number is never a word: "1546" alone has nothing to compare
        assertThat(suggestion.suggest("1546", list)).isInstanceOf(NameSuggestion.NoSuggestion.class);
    }

    @Test
    void groupAccountAndDescriptionMarksCompareOnlyTheDescription() {
        // O2: the account printed in 1.1.1 is a group one; unmarked it ties with 1.1.5, marked the holidays go to 1.1.5
        BudgetLine group = extraLine("1.1.1", "1224 - Férias", "Salários");
        BudgetLine holidays = extraLine("1.1.5", "1553 - Provisão", "Férias");
        var pair = List.of(group, holidays);
        assertThat(suggestion.suggest("FÉRIAS", List.of(), pair)).isInstanceOf(NameSuggestion.NoSuggestion.class);

        group.setMarks(true, false);
        var r = suggestion.suggest("FÉRIAS", List.of(), pair, NameSuggestion.LineHints.of(pair));
        assertThat(((NameSuggestion.Suggested) r).line().getEffectiveCode()).isEqualTo("1.1.5");

        // O1: "vale a descrição" — the printed account no longer counts for the line
        BudgetLine bonus = extraLine("1.1.2", "1240 - Gratificações", "Vale transporte");
        bonus.setMarks(false, true);
        var single = List.of(bonus);
        assertThat(suggestion.suggest("GRATIFICAÇÕES", List.of(), single)).isInstanceOf(NameSuggestion.Suggested.class);
        assertThat(suggestion.suggest("GRATIFICAÇÕES", List.of(), single, NameSuggestion.LineHints.of(single)))
                .isInstanceOf(NameSuggestion.NoSuggestion.class);
    }

    private static BudgetLine extraLine(String code, String account, String description) {
        var budget = new Budget(UUID.randomUUID(), UUID.randomUUID(), "b".repeat(64));
        return new BudgetLine(budget, 999, 1, BudgetLineType.LINE, code, account, null, null, description,
                java.math.BigDecimal.ZERO.setScale(2), java.math.BigDecimal.ONE.setScale(2), null, null);
    }
}
