package br.com.condominioauditoria.api.service.calculator;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.enums.BudgetLineMark;
import br.com.condominioauditoria.api.model.enums.BudgetLineType;
import br.com.condominioauditoria.api.model.enums.SpendingProfile;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** RF-13.3 and ADR 0007, Decision 2: the fixed rules of the suggested spending profile, with the pilot's lines. */
class SpendingProfileSuggestionTest {

    private final Budget budget = new Budget(UUID.randomUUID(), UUID.randomUUID(), "a".repeat(64));

    @Test
    void provisionsOfThePilot() {
        assertThat(profile("1.1.5", "1553 - Férias", null, "Provisão de Férias", null))
                .isEqualTo(SpendingProfile.PROVISION);
        assertThat(profile("1.1.6", "1554 - Rescisões", null, "Rescisões Trabalhistas", null))
                .isEqualTo(SpendingProfile.PROVISION);
        assertThat(profile("1.1.12", "1556 - 13º Salário", null, "13º Salário", null))
                .isEqualTo(SpendingProfile.PROVISION);
    }

    @Test
    void annualLinesByTheObservations() {
        assertThat(profile("1.6.20", "1347 - Assembleias", null, "Assembleias", "média 3 assembléias no ano"))
                .isEqualTo(SpendingProfile.ANNUAL);
        assertThat(profile("1.6.21", "1346 - Outros Serviços Contratados", null, "E-mail GoDaddy",
                "Pagamento anual R$ 2.650")).isEqualTo(SpendingProfile.ANNUAL);
    }

    @Test
    void separateApportionmentByTheMark() {
        assertThat(profile("1.4.1", null, BudgetLineMark.SEPARATE_APPORTIONMENT, "Força e Luz", null))
                .isEqualTo(SpendingProfile.SEPARATE_APPORTIONMENT);
        // The mark wins over the other rules
        assertThat(profile("1.6.15", null, BudgetLineMark.SEPARATE_APPORTIONMENT, "Seguro predial", "anual"))
                .isEqualTo(SpendingProfile.SEPARATE_APPORTIONMENT);
    }

    @Test
    void everythingElseIsMonthly() {
        // An account numbered 13xx is not "13º": the account code is ignored
        assertThat(profile("1.6.21", "1346 - Outros Serviços Contratados", null, "E-mail GoDaddy", null))
                .isEqualTo(SpendingProfile.MONTHLY);
        assertThat(profile("1.8.1", "1693 - Serviços", null, "Serviços", "meses nov à fev"))
                .isEqualTo(SpendingProfile.MONTHLY);
        // "manual" is not "anual"; 130 is not 13
        assertThat(profile("1.7.1", "1601 - Material", null, "Material", "conforme manual")).isEqualTo(
                SpendingProfile.MONTHLY);
        assertThat(profile("1.7.2", "1602 - Lâmpadas", null, "130 lâmpadas", null)).isEqualTo(SpendingProfile.MONTHLY);
    }

    @Test
    void reasonSaysWhichRuleMatched() {
        BudgetLine l = line("1.1.5", "1553 - Férias", null, "Provisão de Férias", null);
        assertThat(SpendingProfileSuggestion.suggest(l).reason())
                .isEqualTo("sugestão (regras v1): conta da PO: \"1553 - Férias\"");
    }

    private SpendingProfile profile(String code, String account, BudgetLineMark mark, String description,
            String notes) {
        return SpendingProfileSuggestion.suggest(line(code, account, mark, description, notes)).profile();
    }

    private BudgetLine line(String code, String account, BudgetLineMark mark, String description, String notes) {
        return new BudgetLine(budget, 1, 1, BudgetLineType.LINE, code, account, null, mark, description,
                BigDecimal.ZERO.setScale(2), new BigDecimal("100.00"), null, notes);
    }
}
