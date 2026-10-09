package br.com.condominioauditoria.api.service.calculator;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.api.config.properties.AccountMappingProperties;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.enums.BudgetLineMark;
import br.com.condominioauditoria.api.model.enums.BudgetLineType;
import br.com.condominioauditoria.api.orcamento.PoDoPiloto;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** RF-03.1.4 and RF-03.1.5: suggestion by name, without AI and never using the account number. */
class NameSuggestionTest {

    private final NameSuggestion suggestion = new NameSuggestion(AccountMappingProperties.defaults());
    private final List<BudgetLine> lines = PoDoPiloto.padrao().linhasGravadas(
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
        assertThat(candidates).noneMatch(l -> l.getMark() == BudgetLineMark.RATEIO_A_PARTE);
        assertThat(candidates).noneMatch(l -> l.getEffectiveCode().startsWith("1.9"));
        assertThat(suggestion.suggest("FUNDO DE RESERVA", candidates)).isInstanceOf(NameSuggestion.NoSuggestion.class);
    }

    @Test
    void sameInputSameSuggestion() {
        var a = suggestion.suggest("MATERIAL HIDRÁULICO", candidates);
        var b = new NameSuggestion(AccountMappingProperties.defaults()).suggest("MATERIAL HIDRÁULICO", candidates);
        assertThat(a.reason()).isEqualTo(b.reason());
    }

    private static BudgetLine extraLine(String code, String account, String description) {
        var budget = new Budget(UUID.randomUUID(), UUID.randomUUID(), "b".repeat(64));
        return new BudgetLine(budget, 999, 1, BudgetLineType.LINHA, code, account, null, null, description,
                java.math.BigDecimal.ZERO.setScale(2), java.math.BigDecimal.ONE.setScale(2), null, null);
    }
}
