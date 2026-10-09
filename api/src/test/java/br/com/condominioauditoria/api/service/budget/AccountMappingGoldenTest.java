package br.com.condominioauditoria.api.service.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import br.com.condominioauditoria.api.dto.response.budget.AccountMappingSummaryResponse;
import br.com.condominioauditoria.api.model.budget.AccountMapping;
import br.com.condominioauditoria.api.model.enums.AccountMappingFilter;
import br.com.condominioauditoria.api.model.enums.AccountMappingSource;
import br.com.condominioauditoria.api.model.enums.AccountMappingStatus;
import br.com.condominioauditoria.api.service.calculator.EffectiveAccountMapping;
import java.util.HashMap;
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
                AccountMappingFilter.TODAS);
        assertThat(list.summary()).isEqualTo(new AccountMappingSummaryResponse(73, 0, 73, 0, 0));
        assertThat(g.scenario.mappings).allMatch(d -> d.getStatus() == AccountMappingStatus.SUGERIDO);
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
        assertThat(plumbing.getSource()).isEqualTo(AccountMappingSource.NOME);
        assertThat(g.scenario.mappings).noneMatch(d -> g.line("1.3.23").getId().equals(d.getBudgetLineId()));
        assertThat(g.scenario.mappings).allMatch(d -> d.getStatus() == AccountMappingStatus.SUGERIDO);
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
        assertThat(r.created()).isGreaterThanOrEqualTo(40);
    }

    private static SeptemberGolden golden() {
        Optional<SeptemberGolden> g = SeptemberGolden.load();
        assumeTrue(g.isPresent(), "golden privado ausente");
        return g.get();
    }
}
