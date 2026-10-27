package br.com.condominioauditoria.api.service.calculator;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.enums.BudgetLineMark;
import br.com.condominioauditoria.api.model.enums.BudgetLineType;
import br.com.condominioauditoria.api.model.enums.SpendingProfile;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * RF-13.14 and ADR 0009, Decision 2: the fixed rules of the suggested spending profile (v2), in their order, with the
 * pilot's lines. Where the P9 proposal differs from the rules (1.1.7, 1.1.11, 1.6.x eventual, 1.3.7 seasonal), the rule
 * stays and the Admin confirms the type: the tests below show the rule.
 */
class SpendingProfileSuggestionTest {

    private static final String STAFF = "PESSOAL";
    private static final String CONTRACTS = "CONTRATOS DE PRESTAÇÃO DE SERVIÇOS";
    private static final String ADMIN = "DESPESAS ADMINISTRATIVAS";
    private static final String GOODS = "AQUISIÇÃO DE BENS";
    private static final String MATERIALS = "MATERIAIS/SUPRIMENTOS";
    private static final String OCCASIONAL_SERVICES = "SERVIÇOS EVENTUAIS";

    private final Budget budget = new Budget(UUID.randomUUID(), UUID.randomUUID(), "a".repeat(64));

    @Test
    void provisionsOfThePilot() {
        assertThat(profile(STAFF, "1553 - Férias", null, "Provisão de Férias", null))
                .isEqualTo(SpendingProfile.PROVISION);
        assertThat(profile(STAFF, "1554 - Rescisões", null, "Rescisões Trabalhistas", null))
                .isEqualTo(SpendingProfile.PROVISION);
        assertThat(profile(STAFF, "1556 - 13º Salário", null, "13º Salário", null))
                .isEqualTo(SpendingProfile.PROVISION);
    }

    @Test
    void annualOnlyWithTheWordAnnualInTheObservations() {
        assertThat(profile(ADMIN, "1346 - Outros Serviços Contratados", null, "E-mail GoDaddy",
                "Pagamento anual R$ 2.650")).isEqualTo(SpendingProfile.ANNUAL);
        // "no ano" is not "anual"; "manual" is not "anual"
        assertThat(profile(ADMIN, "1347 - Assembleias", null, "Assembleias", "média 3 assembléias no ano"))
                .isEqualTo(SpendingProfile.RECURRING);
        assertThat(profile(MATERIALS, "1601 - Material", null, "Material", "conforme manual"))
                .isEqualTo(SpendingProfile.OCCASIONAL);
    }

    @Test
    void seasonalByMonthsOfTheYear() {
        assertThat(profile(OCCASIONAL_SERVICES, "1693 - Serviços", null, "Apoio piscina", "meses nov à fev"))
                .isEqualTo(SpendingProfile.SEASONAL);
        assertThat(profile(OCCASIONAL_SERVICES, "1693 - Serviços", null, "Apoio correspondência nov a jan", null))
                .isEqualTo(SpendingProfile.SEASONAL);
        // A month followed by a year is the period of an average, not a season
        assertThat(profile(CONTRACTS, "1692 - Vigia e Portaria", null, "Join Facilities",
                "Média jan 26/abr 26 + VIGILANTES RJ")).isEqualTo(SpendingProfile.RECURRING);
        assertThat(profile(CONTRACTS, "1690 - Jardinagem", null, "3JF", "média ago/25 a abr/26"))
                .isEqualTo(SpendingProfile.RECURRING);
    }

    @Test
    void occasionalByTheGroupNameReadInTheBudget() {
        assertThat(profile(GOODS, "1621 - Equipamentos", null, "Equipamentos", null))
                .isEqualTo(SpendingProfile.OCCASIONAL);
        assertThat(profile(MATERIALS, "1602 - Lâmpadas", null, "130 lâmpadas", null))
                .isEqualTo(SpendingProfile.OCCASIONAL);
        assertThat(profile(OCCASIONAL_SERVICES, "1693 - Serviços", null, "Dedetização", null))
                .isEqualTo(SpendingProfile.OCCASIONAL);
        // Without a group, the group rule does not apply
        assertThat(profile(null, "1621 - Equipamentos", null, "Equipamentos", null))
                .isEqualTo(SpendingProfile.RECURRING);
    }

    @Test
    void separateApportionmentByTheMarkWinsOverEveryRule() {
        assertThat(profile(ADMIN, null, BudgetLineMark.SEPARATE_APPORTIONMENT, "Força e Luz", null))
                .isEqualTo(SpendingProfile.SEPARATE_APPORTIONMENT);
        assertThat(profile(ADMIN, null, BudgetLineMark.SEPARATE_APPORTIONMENT, "Seguro predial", "anual"))
                .isEqualTo(SpendingProfile.SEPARATE_APPORTIONMENT);
    }

    @Test
    void everythingElseIsRecurring() {
        // An account numbered 13xx is not "13º": the account code is ignored; 130 is not 13
        assertThat(profile(ADMIN, "1346 - Outros Serviços Contratados", null, "Internet", null))
                .isEqualTo(SpendingProfile.RECURRING);
        assertThat(profile(STAFF, "1546 - Salário", null, "Salários", null)).isEqualTo(SpendingProfile.RECURRING);
        assertThat(profile(CONTRACTS, "1690 - Jardinagem", null, "Jardinagem", null))
                .isEqualTo(SpendingProfile.RECURRING);
    }

    @Test
    void suggestAllUsesTheGroupOfEachLineAndSkipsTheFunds() {
        List<BudgetLine> lines = new ArrayList<>();
        lines.add(row(1, BudgetLineType.TOTAL, "1", null, "TOTAL", null));
        lines.add(row(2, BudgetLineType.GROUP, "1.5", null, GOODS, null));
        BudgetLine goods = row(3, BudgetLineType.LINE, "1.5.1", "1621 - Equipamentos", "Equipamentos", null);
        lines.add(goods);
        lines.add(row(4, BudgetLineType.GROUP, "1.6", null, ADMIN, null));
        BudgetLine mail = row(5, BudgetLineType.LINE, "1.6.21", "1346 - Outros", "E-mail", "pagamento anual");
        lines.add(mail);
        lines.add(row(6, BudgetLineType.GROUP, "1.9", null, "FUNDOS", null));
        BudgetLine fund = row(7, BudgetLineType.LINE, "1.9.1", null, "Fundo de reserva", null);
        lines.add(fund);
        Map<UUID, SpendingProfileSuggestion.Suggestion> all = SpendingProfileSuggestion.suggestAll(
                BudgetStructure.of(lines));
        assertThat(all.get(goods.getId()).profile()).isEqualTo(SpendingProfile.OCCASIONAL);
        assertThat(all.get(goods.getId()).reason())
                .isEqualTo("sugestão (regras v2): grupo da PO: \"AQUISIÇÃO DE BENS\"");
        assertThat(all.get(mail.getId()).profile()).isEqualTo(SpendingProfile.ANNUAL);
        assertThat(all).doesNotContainKey(fund.getId());
    }

    @Test
    void reasonSaysWhichRuleMatched() {
        BudgetLine l = line("1553 - Férias", null, "Descanso remunerado", null);
        assertThat(SpendingProfileSuggestion.suggest(l, STAFF).reason())
                .isEqualTo("sugestão (regras v2): conta da PO: \"1553 - Férias\"");
    }

    private SpendingProfile profile(String group, String account, BudgetLineMark mark, String description,
            String notes) {
        return SpendingProfileSuggestion.suggest(line(account, mark, description, notes), group).profile();
    }

    private BudgetLine line(String account, BudgetLineMark mark, String description, String notes) {
        return new BudgetLine(budget, 1, 1, BudgetLineType.LINE, "1.x", account, null, mark, description,
                BigDecimal.ZERO.setScale(2), new BigDecimal("100.00"), null, notes);
    }

    private BudgetLine row(int position, BudgetLineType type, String code, String account, String description,
            String notes) {
        return new BudgetLine(budget, position, 1, type, code, account, null, null, description,
                BigDecimal.ZERO.setScale(2), new BigDecimal("100.00"), null, notes);
    }
}
