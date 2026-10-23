package br.com.condominioauditoria.api.service.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import br.com.condominioauditoria.api.dto.response.budget.AccountMappingSummaryResponse;
import br.com.condominioauditoria.api.model.budget.AccountMapping;
import br.com.condominioauditoria.api.model.enums.AccountMappingFilter;
import br.com.condominioauditoria.api.model.enums.AccountMappingSource;
import br.com.condominioauditoria.api.model.enums.AccountMappingStatus;
import br.com.condominioauditoria.api.service.calculator.EffectiveAccountMapping;
import br.com.condominioauditoria.api.dto.request.budget.BudgetConfirmationRequest;
import br.com.condominioauditoria.api.messaging.GoldenMessages;
import br.com.condominioauditoria.api.model.budget.Budget;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Account mapping golden test (RF-03.1.4, RF-03.1.5 and RF-03.1.15) with the real budget and cash flow of September
 * 2026 and the pilot's map of 73 accounts. Skipped without data/golden/privado.
 */
class AccountMappingGoldenTest {

    @Test
    void sheetOf73AccountsComesInSuggestedAndLinksNoAccount() {
        SeptemberGolden g = golden();
        Optional<String> map = SeptemberGolden.map();
        assumeTrue(map.isPresent(), "mapa do piloto ausente no golden privado");

        var r = g.scenario.accountMapping.loadSheet(g.scenario.condominiumId, g.budget.getId(),
                "mapa-contas-fluxo-para-PO.csv",
                map.get(), "admin");

        assertThat(r.rejected()).isEmpty();
        assertThat(r.skipped()).isEmpty();
        assertThat(r.accepted()).isEqualTo(73);
        var list = g.scenario.accountMapping.list(g.scenario.condominiumId, g.budget.getId(),
                AccountMappingFilter.ALL);
        assertThat(list.summary()).isEqualTo(new AccountMappingSummaryResponse(73, 0, 73, 0, 0));
        assertThat(g.scenario.mappings).allMatch(d -> d.getStatus() == AccountMappingStatus.SUGGESTED);
        // Suggested links no account: the numbers only use the confirmed (the month's calculation is in step 7)
        assertThat(EffectiveAccountMapping.confirmed(g.scenario.mappings)).isEmpty();

        AccountMapping plumbing = g.scenario.mappings.stream().filter(d -> d.getAccountCode().equals("1621")).findFirst()
                .orElseThrow();
        assertThat(plumbing.getBudgetLineId()).isEqualTo(g.line("1.7.8").getId()).isNotEqualTo(g.line("1.3.23").getId());
        assertThat(g.scenario.mappings.stream().filter(d -> d.getAccountCode().equals("1073")).findFirst().orElseThrow()
                .getBudgetLineId()).isEqualTo(g.line("1.3.25").getId());
        assertThat(g.scenario.mappingEvents).hasSize(73);
    }

    @Test
    void nameSuggestionLinks1621To178AndNeverTo1323() {
        SeptemberGolden g = golden();

        var r = g.scenario.accountMapping.suggest(g.scenario.condominiumId, g.budget.getId(), "admin");

        AccountMapping plumbing = g.scenario.mappings.stream().filter(d -> d.getAccountCode().equals("1621")).findFirst()
                .orElseThrow();
        assertThat(plumbing.getBudgetLineId()).isEqualTo(g.line("1.7.8").getId());
        assertThat(plumbing.getSource()).isEqualTo(AccountMappingSource.NAME);
        assertThat(g.scenario.mappings).noneMatch(d -> g.line("1.3.23").getId().equals(d.getBudgetLineId()));
        assertThat(g.scenario.mappings).allMatch(d -> d.getStatus() == AccountMappingStatus.SUGGESTED);
        assertThat(r.created() + r.withoutSuggestion().size()).isEqualTo(73);

        // Measure of the suggestion by name against the pilot's map: no suggestion disagrees with the map
        Optional<String> map = SeptemberGolden.map();
        assumeTrue(map.isPresent());
        Map<String, String> expected = new HashMap<>();
        map.get().lines().skip(1).map(l -> l.split(";")).forEach(c -> expected.put(c[0], c[1]));
        Map<java.util.UUID, String> code = new HashMap<>();
        g.scenario.lines.forEach(l -> code.put(l.getId(), l.getEffectiveCode()));
        long valid = g.scenario.mappings.stream()
                .filter(d -> code.get(d.getBudgetLineId()).equals(expected.get(d.getAccountCode()))).count();
        assertThat(valid).isEqualTo(r.created());
        // Q51: 43 by the Jaccard score + 3 "every word in the line" + 3 ties broken by the supplier
        assertThat(r.created()).isGreaterThanOrEqualTo(49);
    }

    @Test
    void withoutClickTheScreenComesWith49SuggestedByNameAndNoneWrong() {
        SeptemberGolden g = golden();
        Optional<String> map = SeptemberGolden.map();
        assumeTrue(map.isPresent());

        // Q51: the cash flow was saved; the listener (or the screen opening) generates by itself
        g.scenario.accountMapping.suggestPending(g.scenario.condominiumId);
        var screen = g.scenario.accountMapping.open(g.scenario.condominiumId, g.budget.getId(), AccountMappingFilter.ALL);

        assertThat(screen.summary()).isEqualTo(new AccountMappingSummaryResponse(73, 0, 49, 0, 24));
        assertThat(screen.suggestedBySource().name()).isEqualTo(49);
        Map<String, String> expected = expected(map.get());
        Map<java.util.UUID, String> code = codes(g);
        assertThat(g.scenario.mappings).hasSize(49).allMatch(d -> d.getStatus() == AccountMappingStatus.SUGGESTED
                && code.get(d.getBudgetLineId()).equals(expected.get(d.getAccountCode())));
        assertThat(g.scenario.mappings).noneMatch(d -> g.line("1.3.23").getId().equals(d.getBudgetLineId()));
        assertThat(EffectiveAccountMapping.confirmed(g.scenario.mappings)).isEmpty();
        int events = g.scenario.mappingEvents.size();
        g.scenario.accountMapping.open(g.scenario.condominiumId, g.budget.getId(), AccountMappingFilter.ALL);
        assertThat(g.scenario.mappingEvents).hasSize(events);
    }

    @Test
    void storedSheetSuggestsThe73AccountsInANewVersionAndOneClickConfirmsThem() {
        SeptemberGolden g = golden();
        Optional<String> map = SeptemberGolden.map();
        assumeTrue(map.isPresent());
        // The sheet loaded once in version 1 (left suggested) is the condominium's sheet from then on
        g.scenario.accountMapping.loadSheet(g.scenario.condominiumId, g.budget.getId(), "mapa-contas-fluxo-para-PO.csv",
                map.get(), "admin");
        assertThat(g.scenario.sheetItems).hasSize(73);
        var readAgain = GoldenMessages.read(GoldenMessages.BUDGET_2026_2027).orElseThrow();
        Budget v2 = g.scenario.readBudget(readAgain.budget(), readAgain.totalsChecks());
        var pilot = g.scenario.pilotRequest(v2);
        g.scenario.confirmation.confirm(g.scenario.condominiumId, v2.getId(), new BudgetConfirmationRequest("2026-10",
                "2027-04", pilot.minutesFileId(), false, pilot.approvalDate(), pilot.effectiveCodes(), pilot.funds(),
                true, false, null), "admin");

        g.scenario.accountMapping.suggestPending(g.scenario.condominiumId);

        var screen = g.scenario.accountMapping.list(g.scenario.condominiumId, v2.getId(), AccountMappingFilter.ALL);
        assertThat(screen.summary()).isEqualTo(new AccountMappingSummaryResponse(73, 0, 73, 0, 0));
        assertThat(screen.suggestedBySource().spreadsheet()).isEqualTo(73);
        Map<String, String> expected = expected(map.get());
        Map<java.util.UUID, String> code = codes(g);
        List<AccountMapping> ofV2 = g.scenario.mappings.stream().filter(d -> d.getBudgetId().equals(v2.getId()))
                .toList();
        assertThat(ofV2).hasSize(73).allMatch(d -> d.getSource() == AccountMappingSource.SPREADSHEET
                && d.getStatus() == AccountMappingStatus.SUGGESTED
                && (d.getBudgetLineId() == null || code.get(d.getBudgetLineId()).equals(expected.get(d.getAccountCode()))));

        int events = g.scenario.mappingEvents.size();
        var r = g.scenario.accountMapping.confirmAllSuggested(g.scenario.condominiumId, v2.getId(), 73, "admin");

        assertThat(r.changed()).isEqualTo(73);
        assertThat(g.scenario.mappingEvents).hasSize(events + 73);
        assertThat(EffectiveAccountMapping.confirmed(ofV2)).hasSize(73);
        // Version 1 stays as it was: suggested, nothing confirmed
        assertThat(EffectiveAccountMapping.confirmed(g.scenario.mappings.stream()
                .filter(d -> d.getBudgetId().equals(g.budget.getId())).toList())).isEmpty();
    }

    private static Map<String, String> expected(String map) {
        Map<String, String> expected = new HashMap<>();
        map.lines().skip(1).map(l -> l.split(";")).forEach(c -> expected.put(c[0], c[1]));
        return expected;
    }

    private static Map<java.util.UUID, String> codes(SeptemberGolden g) {
        Map<java.util.UUID, String> code = new HashMap<>();
        g.scenario.lines.forEach(l -> code.put(l.getId(), l.getEffectiveCode()));
        return code;
    }

    private static SeptemberGolden golden() {
        Optional<SeptemberGolden> g = SeptemberGolden.load();
        assumeTrue(g.isPresent(), "golden privado ausente");
        return g.get();
    }
}
