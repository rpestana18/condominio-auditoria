package br.com.condominioauditoria.api.service.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.condominioauditoria.api.dto.request.budget.AccountMappingBatchRequest;
import br.com.condominioauditoria.api.dto.request.budget.BudgetConfirmationRequest;
import br.com.condominioauditoria.api.dto.request.budget.MappingTargetRequest;
import br.com.condominioauditoria.api.dto.response.budget.AccountMappingResponse;
import br.com.condominioauditoria.api.dto.response.budget.AccountMappingSummaryResponse;
import br.com.condominioauditoria.api.dto.response.budget.AccountWithoutSuggestionResponse;
import br.com.condominioauditoria.api.dto.response.budget.SkippedAccountResponse;
import br.com.condominioauditoria.api.model.budget.AccountMapping;
import br.com.condominioauditoria.api.model.budget.AccountMappingEvent;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.enums.AccountMappingAction;
import br.com.condominioauditoria.api.model.enums.AccountMappingBatchAction;
import br.com.condominioauditoria.api.model.enums.AccountMappingFilter;
import br.com.condominioauditoria.api.model.enums.AccountMappingSource;
import br.com.condominioauditoria.api.model.enums.AccountMappingStatus;
import br.com.condominioauditoria.api.model.enums.MappingTargetType;
import br.com.condominioauditoria.api.orcamento.CenarioPo;
import br.com.condominioauditoria.api.orcamento.PoDoPiloto;
import br.com.condominioauditoria.api.service.calculator.EffectiveAccountMapping;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/**
 * ADR 0004, step 6: mapping per budget version, suggestions that never confirm themselves and a trail of every change.
 */
class AccountMappingServiceTest {

    private final CenarioPo scenario = new CenarioPo();
    private Budget budget;

    @BeforeEach
    void prepare() {
        budget = scenario.poConfirmada();
        LocalDate set = LocalDate.of(2026, 9, 10);
        scenario.debito("1621", "MATERIAL HIDRÁULICO", "4949.99", set);
        scenario.debito("1108", "PRO LABORE", "7120.00", set);
        scenario.debito("1324", "ESTORNOS", "3987.12", set);
        // Outside the fiscal year (04/2026): not on the list
        scenario.debito("9999", "CONTA ANTIGA", "10.00", LocalDate.of(2026, 4, 30));
    }

    @Test
    void nameSuggestionsStaySuggestedWithReasonAndTrail() {
        var r = scenario.depara.suggest(scenario.condominioId, budget.getId(), "admin");

        assertThat(r.byName()).isEqualTo(1);
        assertThat(r.withoutSuggestion()).extracting(AccountWithoutSuggestionResponse::account).containsExactly("1108",
                "1324");
        AccountMapping d = single("1621");
        assertThat(d.getStatus()).isEqualTo(AccountMappingStatus.SUGERIDO);
        assertThat(d.getSource()).isEqualTo(AccountMappingSource.NOME);
        assertThat(d.getBudgetLineId()).isEqualTo(scenario.linha(budget, "1.7.8", 0).getId());
        assertThat(d.getBudgetLineId()).isNotEqualTo(scenario.linha(budget, "1.3.23", 0).getId());
        assertThat(d.getReason()).contains("MATERIAL HIDRAULICO");
        assertThat(scenario.eventosDepara).singleElement().satisfies(e -> {
            assertThat(e.getAction()).isEqualTo(AccountMappingAction.SUGERIDO);
            assertThat(e.getPreviousTarget()).isNull();
            assertThat(e.getNewTarget()).startsWith("1.7.8");
            assertThat(e.getUsername()).isEqualTo("admin");
        });

        var list = scenario.depara.list(scenario.condominioId, budget.getId(), AccountMappingFilter.TODAS);
        assertThat(list.summary()).isEqualTo(new AccountMappingSummaryResponse(3, 0, 1, 0, 2));
        assertThat(list.accounts()).extracting(AccountMappingResponse::account).containsExactly("1108", "1324", "1621");
        assertThat(EffectiveAccountMapping.confirmed(scenario.deparas)).isEmpty();
    }

    @Test
    void suggestingTwiceDoesNotDuplicate() {
        scenario.depara.suggest(scenario.condominioId, budget.getId(), "admin");
        var r = scenario.depara.suggest(scenario.condominioId, budget.getId(), "admin");

        assertThat(r.created()).isZero();
        assertThat(scenario.deparas).hasSize(1);
        assertThat(scenario.eventosDepara).hasSize(1);
    }

    @Test
    void sheetComesInSuggestedAndConfirmedDoesNotChange() {
        var r = scenario.depara.loadSheet(scenario.condominioId, budget.getId(), "mapa.csv", """
                1621;1.7.8
                1108;1.3.20
                1324;AJUSTE (estorno)
                7777;1.9.1
                """, "admin");

        assertThat(r.accepted()).isEqualTo(3);
        assertThat(r.rejected()).singleElement().satisfies(x -> assertThat(x.line()).isEqualTo(4));
        assertThat(scenario.deparas).allMatch(d -> d.getStatus() == AccountMappingStatus.SUGERIDO
                && d.getSource() == AccountMappingSource.PLANILHA);
        assertThat(EffectiveAccountMapping.confirmed(scenario.deparas)).isEmpty();

        scenario.depara.batch(scenario.condominioId, budget.getId(),
                new AccountMappingBatchRequest(AccountMappingBatchAction.CONFIRMAR, List.of("1621")), "admin");
        var second = scenario.depara.loadSheet(scenario.condominioId, budget.getId(), "mapa.csv", "1621;1.3.20\n",
                "admin");
        assertThat(second.accepted()).isZero();
        assertThat(second.skipped()).singleElement().satisfies(i -> assertThat(i.reason()).contains("já confirmada"));
        assertThat(single("1621").getBudgetLineId()).isEqualTo(scenario.linha(budget, "1.7.8", 0).getId());
    }

    @Test
    void batchConfirmsAndRejectsWithOneEventPerAccount() {
        scenario.depara.loadSheet(scenario.condominioId, budget.getId(), null, "1621;1.7.8\n1108;1.3.20\n1324;AJUSTE\n",
                "admin");
        int before = scenario.eventosDepara.size();

        var r = scenario.depara.batch(scenario.condominioId, budget.getId(),
                new AccountMappingBatchRequest(AccountMappingBatchAction.CONFIRMAR, List.of("1621", "1108", "1324",
                        "5555")), "admin");

        assertThat(r.changed()).isEqualTo(3);
        assertThat(r.skipped()).extracting(SkippedAccountResponse::account).containsExactly("5555");
        assertThat(scenario.eventosDepara.subList(before, scenario.eventosDepara.size()))
                .hasSize(3).allMatch(e -> e.getAction() == AccountMappingAction.CONFIRMADO
                        && e.getPreviousStatus() == AccountMappingStatus.SUGERIDO && e.getNewStatus() == AccountMappingStatus.CONFIRMADO);
        assertThat(EffectiveAccountMapping.confirmed(scenario.deparas)).containsOnlyKeys("1108", "1324", "1621");

        scenario.depara.batch(scenario.condominioId, budget.getId(),
                new AccountMappingBatchRequest(AccountMappingBatchAction.RECUSAR, List.of("1324")), "admin");
        assertThat(EffectiveAccountMapping.confirmed(scenario.deparas)).containsOnlyKeys("1108", "1621");
        assertThat(scenario.depara.list(scenario.condominioId, budget.getId(),
                AccountMappingFilter.PENDENTES).accounts())
                .extracting(AccountMappingResponse::account).containsExactly("1324");
    }

    @Test
    void targetChangeRecordsPreviousAndNew() {
        scenario.depara.setTarget(scenario.condominioId, budget.getId(), "1108",
                new MappingTargetRequest(MappingTargetType.LINHA_PO, scenario.linha(budget, "1.3.20", 0).getId(), null,
                        null), "admin");
        scenario.depara.setTarget(scenario.condominioId, budget.getId(), "1108",
                new MappingTargetRequest(MappingTargetType.LINHA_PO, scenario.linha(budget, "1.3.1", 0).getId(), null,
                        true), "admin2");

        AccountMappingEvent change = scenario.eventosDepara.getLast();
        assertThat(change.getAction()).isEqualTo(AccountMappingAction.ALTERADO);
        assertThat(change.getPreviousTarget()).startsWith("1.3.20");
        assertThat(change.getNewTarget()).startsWith("1.3.1 ");
        assertThat(change.getUsername()).isEqualTo("admin2");
        assertThat(change.getPreviousStatus()).isEqualTo(AccountMappingStatus.CONFIRMADO);
        assertThat(scenario.depara.events(scenario.condominioId, budget.getId())).hasSize(2);
    }

    @Test
    void invalidTargetIsRejected() {
        assertThatThrownBy(() -> scenario.depara.setTarget(scenario.condominioId, budget.getId(), "1108",
                new MappingTargetRequest(MappingTargetType.LINHA_PO, scenario.linha(budget, "1.9.1", 0).getId(), null,
                        null), "admin"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("1.9");
        assertThatThrownBy(() -> scenario.depara.setTarget(scenario.condominioId, budget.getId(), "1108",
                new MappingTargetRequest(MappingTargetType.LINHA_PO, scenario.linha(budget, "1.3", 0).getId(), null,
                        null), "admin"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> scenario.depara.setTarget(scenario.condominioId, budget.getId(), "abc",
                new MappingTargetRequest(MappingTargetType.AJUSTE, null, null, null), "admin"))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(scenario.deparas).isEmpty();
    }

    @Test
    void unconfirmedBudgetHasNoMapping() {
        Budget read = scenario.lerPo(PoDoPiloto.padrao());

        assertThatThrownBy(() -> scenario.depara.suggest(scenario.condominioId, read.getId(), "admin"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Confirme a PO");
    }

    @Test
    void newVersionGetsPreviousAsSuggestionWithoutInheritingConfirmation() {
        scenario.depara.loadSheet(scenario.condominioId, budget.getId(), null, "1621;1.7.8\n1324;AJUSTE (estorno)\n",
                "admin");
        scenario.depara.batch(scenario.condominioId, budget.getId(),
                new AccountMappingBatchRequest(AccountMappingBatchAction.CONFIRMAR, List.of("1621", "1324")),
                "admin");
        Budget v2 = scenario.lerPo(PoDoPiloto.padrao());
        var request = scenario.pedidoDoPiloto(v2);
        scenario.confirmacao.confirm(scenario.condominioId, v2.getId(), new BudgetConfirmationRequest("2026-10",
                "2027-04",
                request.minutesFileId(), false, request.approvalDate(), request.effectiveCodes(), request.funds(), true,
                false, null), "admin");

        var r = scenario.depara.suggest(scenario.condominioId, v2.getId(), "admin");

        assertThat(r.fromPreviousVersion()).isEqualTo(2);
        var same = scenario.depara.list(scenario.condominioId, v2.getId(), AccountMappingFilter.IGUAIS_VERSAO_ANTERIOR);
        assertThat(same.accounts()).extracting(AccountMappingResponse::account).containsExactly("1324", "1621");
        assertThat(same.accounts()).allMatch(c -> c.status() == AccountMappingStatus.SUGERIDO
                && c.source() == AccountMappingSource.VERSAO_ANTERIOR && c.reason().startsWith("igual à versão anterior"));
        assertThat(same.accounts().get(1).target().lineId()).isEqualTo(scenario.linha(v2, "1.7.8", 0).getId());
        // Version 1 does not change; version 2 does not inherit confirmation
        assertThat(EffectiveAccountMapping.confirmed(scenario.deparas.stream()
                .filter(d -> d.getBudgetId().equals(budget.getId())).toList())).containsOnlyKeys("1324", "1621");
        assertThat(EffectiveAccountMapping.confirmed(scenario.deparas.stream()
                .filter(d -> d.getBudgetId().equals(v2.getId())).toList())).isEmpty();

        scenario.depara.batch(scenario.condominioId, v2.getId(),
                new AccountMappingBatchRequest(AccountMappingBatchAction.CONFIRMAR,
                        same.accounts().stream().map(AccountMappingResponse::account).toList()),
                "admin");
        assertThat(scenario.depara.list(scenario.condominioId, v2.getId(), null).summary().confirmed()).isEqualTo(2);
    }

    private AccountMapping single(String account) {
        return scenario.deparas.stream().filter(d -> d.getAccountCode().equals(account)).findFirst().orElseThrow();
    }
}
