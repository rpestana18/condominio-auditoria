package br.com.condominioauditoria.api.service.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import br.com.condominioauditoria.api.dto.response.budget.AccountMappingSummaryResponse;
import br.com.condominioauditoria.api.model.budget.AccountMapping;
import br.com.condominioauditoria.api.model.enums.AccountMappingFilter;
import br.com.condominioauditoria.api.model.enums.AccountMappingSource;
import br.com.condominioauditoria.api.model.enums.AccountMappingStatus;
import br.com.condominioauditoria.api.orcamento.GoldenSetembro;
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
        GoldenSetembro g = golden();
        Optional<String> map = GoldenSetembro.mapa();
        assumeTrue(map.isPresent(), "mapa do piloto ausente no golden privado");

        var r = g.cenario.depara.loadSheet(g.cenario.condominioId, g.po.getId(), "mapa-contas-fluxo-para-PO.csv",
                map.get(), "admin");

        assertThat(r.rejected()).isEmpty();
        assertThat(r.skipped()).isEmpty();
        assertThat(r.accepted()).isEqualTo(73);
        var list = g.cenario.depara.list(g.cenario.condominioId, g.po.getId(), AccountMappingFilter.TODAS);
        assertThat(list.summary()).isEqualTo(new AccountMappingSummaryResponse(73, 0, 73, 0, 0));
        assertThat(g.cenario.deparas).allMatch(d -> d.getStatus() == AccountMappingStatus.SUGERIDO);
        // Suggested links no account: the numbers only use the confirmed (the month's calculation is in step 7)
        assertThat(EffectiveAccountMapping.confirmed(g.cenario.deparas)).isEmpty();

        AccountMapping plumbing = g.cenario.deparas.stream().filter(d -> d.getAccountCode().equals("1621")).findFirst()
                .orElseThrow();
        assertThat(plumbing.getBudgetLineId()).isEqualTo(g.linha("1.7.8").getId()).isNotEqualTo(g.linha("1.3.23").getId());
        assertThat(g.cenario.deparas.stream().filter(d -> d.getAccountCode().equals("1073")).findFirst().orElseThrow()
                .getBudgetLineId()).isEqualTo(g.linha("1.3.25").getId());
        assertThat(g.cenario.eventosDepara).hasSize(73);
    }

    @Test
    void nameSuggestionLinks1621To178AndNeverTo1323() {
        GoldenSetembro g = golden();

        var r = g.cenario.depara.suggest(g.cenario.condominioId, g.po.getId(), "admin");

        AccountMapping plumbing = g.cenario.deparas.stream().filter(d -> d.getAccountCode().equals("1621")).findFirst()
                .orElseThrow();
        assertThat(plumbing.getBudgetLineId()).isEqualTo(g.linha("1.7.8").getId());
        assertThat(plumbing.getSource()).isEqualTo(AccountMappingSource.NOME);
        assertThat(g.cenario.deparas).noneMatch(d -> g.linha("1.3.23").getId().equals(d.getBudgetLineId()));
        assertThat(g.cenario.deparas).allMatch(d -> d.getStatus() == AccountMappingStatus.SUGERIDO);
        assertThat(r.created() + r.withoutSuggestion().size()).isEqualTo(73);

        // Measure of the suggestion by name against the pilot's map: no suggestion disagrees with the map
        Optional<String> map = GoldenSetembro.mapa();
        assumeTrue(map.isPresent());
        Map<String, String> expected = new HashMap<>();
        map.get().lines().skip(1).map(l -> l.split(";")).forEach(c -> expected.put(c[0], c[1]));
        Map<java.util.UUID, String> code = new HashMap<>();
        g.cenario.linhas.forEach(l -> code.put(l.getId(), l.getEffectiveCode()));
        long valid = g.cenario.deparas.stream()
                .filter(d -> code.get(d.getBudgetLineId()).equals(expected.get(d.getAccountCode()))).count();
        assertThat(valid).isEqualTo(r.created());
        assertThat(r.created()).isGreaterThanOrEqualTo(40);
    }

    private static GoldenSetembro golden() {
        Optional<GoldenSetembro> g = GoldenSetembro.carregar();
        assumeTrue(g.isPresent(), "golden privado ausente");
        return g.get();
    }
}
