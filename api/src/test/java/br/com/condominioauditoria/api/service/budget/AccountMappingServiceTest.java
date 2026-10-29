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
import br.com.condominioauditoria.api.service.calculator.EffectiveAccountMapping;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/**
 * ADR 0004, step 6, and ADR 0009, Decision 8 (Q54): mapping per budget version, automatic links that count without
 * confirmation, the Admin's choice that is never overwritten and a trail of every change.
 */
class AccountMappingServiceTest {

    private final BudgetScenario scenario = new BudgetScenario();
    private Budget budget;

    @BeforeEach
    void prepare() {
        budget = scenario.confirmedBudget();
        LocalDate set = LocalDate.of(2026, 9, 10);
        scenario.debit("1621", "MATERIAL HIDRÁULICO", "4949.99", set);
        scenario.debit("1108", "PRO LABORE", "7120.00", set);
        scenario.debit("1324", "ESTORNOS", "3987.12", set);
        // Outside the fiscal year (04/2026): not on the list
        scenario.debit("9999", "CONTA ANTIGA", "10.00", LocalDate.of(2026, 4, 30));
    }

    @Test
    void nameLinksAreAutomaticAndCountWithReasonAndTrail() {
        var r = scenario.accountMapping.suggest(scenario.condominiumId, budget.getId(), "admin");

        assertThat(r.byName()).isEqualTo(1);
        assertThat(r.withoutSuggestion()).extracting(AccountWithoutSuggestionResponse::account).containsExactly("1108",
                "1324");
        AccountMapping d = single("1621");
        assertThat(d.getStatus()).isEqualTo(AccountMappingStatus.AUTOMATIC);
        assertThat(d.getSource()).isEqualTo(AccountMappingSource.NAME);
        assertThat(d.isLowConfidence()).isFalse();
        assertThat(d.getBudgetLineId()).isEqualTo(scenario.line(budget, "1.7.8", 0).getId());
        assertThat(d.getBudgetLineId()).isNotEqualTo(scenario.line(budget, "1.3.23", 0).getId());
        assertThat(d.getReason()).contains("MATERIAL HIDRAULICO");
        assertThat(scenario.mappingEvents).singleElement().satisfies(e -> {
            assertThat(e.getAction()).isEqualTo(AccountMappingAction.AUTOMATIC);
            assertThat(e.getPreviousTarget()).isNull();
            assertThat(e.getNewTarget()).startsWith("1.7.8");
            assertThat(e.getUsername()).isEqualTo("admin");
        });

        var list = scenario.accountMapping.list(scenario.condominiumId, budget.getId(), AccountMappingFilter.ALL);
        assertThat(list.summary()).isEqualTo(new AccountMappingSummaryResponse(3, 1, 0, 0, 0, 2));
        assertThat(list.accounts()).extracting(AccountMappingResponse::account).containsExactly("1108", "1324", "1621");
        // Q54: the automatic link counts in the numbers without confirmation
        assertThat(EffectiveAccountMapping.effective(scenario.mappings)).containsOnlyKeys("1621");
        // Only the exceptions: the two accounts without a link found
        assertThat(scenario.accountMapping.list(scenario.condominiumId, budget.getId(), AccountMappingFilter.EXCEPTIONS)
                .accounts()).extracting(AccountMappingResponse::account).containsExactly("1108", "1324");
    }

    @Test
    void suggestingTwiceDoesNotDuplicate() {
        scenario.accountMapping.suggest(scenario.condominiumId, budget.getId(), "admin");
        var r = scenario.accountMapping.suggest(scenario.condominiumId, budget.getId(), "admin");

        assertThat(r.created()).isZero();
        assertThat(scenario.mappings).hasSize(1);
        assertThat(scenario.mappingEvents).hasSize(1);
    }

    @Test
    void sheetComesInAutomaticAndConfirmedDoesNotChange() {
        var r = scenario.accountMapping.loadSheet(scenario.condominiumId, budget.getId(), "mapa.csv", """
                1621;1.7.8
                1108;1.3.20
                1324;AJUSTE (estorno)
                7777;1.9.1
                """, "admin");

        assertThat(r.accepted()).isEqualTo(3);
        assertThat(r.rejected()).singleElement().satisfies(x -> assertThat(x.line()).isEqualTo(4));
        assertThat(scenario.mappings).allMatch(d -> d.getStatus() == AccountMappingStatus.AUTOMATIC
                && d.getSource() == AccountMappingSource.SPREADSHEET);
        assertThat(EffectiveAccountMapping.effective(scenario.mappings)).containsOnlyKeys("1108", "1324", "1621");

        scenario.accountMapping.batch(scenario.condominiumId, budget.getId(),
                new AccountMappingBatchRequest(AccountMappingBatchAction.CONFIRM, List.of("1621")), "admin");
        var second = scenario.accountMapping.loadSheet(scenario.condominiumId, budget.getId(), "mapa.csv",
                "1621;1.3.20\n",
                "admin");
        assertThat(second.accepted()).isZero();
        assertThat(second.skipped()).singleElement().satisfies(i -> assertThat(i.reason()).contains("já confirmado"));
        assertThat(single("1621").getBudgetLineId()).isEqualTo(scenario.line(budget, "1.7.8", 0).getId());
    }

    @Test
    void batchConfirmsAndRejectsWithOneEventPerAccount() {
        scenario.accountMapping.loadSheet(scenario.condominiumId, budget.getId(), null,
                "1621;1.7.8\n1108;1.3.20\n1324;AJUSTE\n",
                "admin");
        int before = scenario.mappingEvents.size();

        var r = scenario.accountMapping.batch(scenario.condominiumId, budget.getId(),
                new AccountMappingBatchRequest(AccountMappingBatchAction.CONFIRM, List.of("1621", "1108", "1324",
                        "5555")), "admin");

        assertThat(r.changed()).isEqualTo(3);
        assertThat(r.skipped()).extracting(SkippedAccountResponse::account).containsExactly("5555");
        assertThat(scenario.mappingEvents.subList(before, scenario.mappingEvents.size()))
                .hasSize(3).allMatch(e -> e.getAction() == AccountMappingAction.CONFIRMED
                        && e.getPreviousStatus() == AccountMappingStatus.AUTOMATIC && e.getNewStatus() == AccountMappingStatus.CONFIRMED);
        assertThat(EffectiveAccountMapping.effective(scenario.mappings)).containsOnlyKeys("1108", "1324", "1621");

        scenario.accountMapping.batch(scenario.condominiumId, budget.getId(),
                new AccountMappingBatchRequest(AccountMappingBatchAction.REJECT, List.of("1324")), "admin");
        assertThat(EffectiveAccountMapping.effective(scenario.mappings)).containsOnlyKeys("1108", "1621");
        assertThat(scenario.accountMapping.list(scenario.condominiumId, budget.getId(),
                AccountMappingFilter.EXCEPTIONS).accounts())
                .extracting(AccountMappingResponse::account).containsExactly("1324");
    }

    @Test
    void targetChangeRecordsPreviousAndNew() {
        scenario.accountMapping.setTarget(scenario.condominiumId, budget.getId(), "1108",
                new MappingTargetRequest(MappingTargetType.BUDGET_LINE, scenario.line(budget, "1.3.20", 0).getId(), null,
                        null), "admin");
        scenario.accountMapping.setTarget(scenario.condominiumId, budget.getId(), "1108",
                new MappingTargetRequest(MappingTargetType.BUDGET_LINE, scenario.line(budget, "1.3.1", 0).getId(), null,
                        true), "admin2");

        AccountMappingEvent change = scenario.mappingEvents.getLast();
        assertThat(change.getAction()).isEqualTo(AccountMappingAction.CHANGED);
        assertThat(change.getPreviousTarget()).startsWith("1.3.20");
        assertThat(change.getNewTarget()).startsWith("1.3.1 ");
        assertThat(change.getUsername()).isEqualTo("admin2");
        assertThat(change.getPreviousStatus()).isEqualTo(AccountMappingStatus.CONFIRMED);
        assertThat(scenario.accountMapping.events(scenario.condominiumId, budget.getId())).hasSize(2);
    }

    @Test
    void invalidTargetIsRejected() {
        assertThatThrownBy(() -> scenario.accountMapping.setTarget(scenario.condominiumId, budget.getId(), "1108",
                new MappingTargetRequest(MappingTargetType.BUDGET_LINE, scenario.line(budget, "1.9.1", 0).getId(), null,
                        null), "admin"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("1.9");
        assertThatThrownBy(() -> scenario.accountMapping.setTarget(scenario.condominiumId, budget.getId(), "1108",
                new MappingTargetRequest(MappingTargetType.BUDGET_LINE, scenario.line(budget, "1.3", 0).getId(), null,
                        null), "admin"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> scenario.accountMapping.setTarget(scenario.condominiumId, budget.getId(), "abc",
                new MappingTargetRequest(MappingTargetType.ADJUSTMENT, null, null, null), "admin"))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(scenario.mappings).isEmpty();
    }

    @Test
    void unconfirmedBudgetHasNoMapping() {
        Budget read = scenario.readBudget(PilotBudget.defaults());

        assertThatThrownBy(() -> scenario.accountMapping.suggest(scenario.condominiumId, read.getId(), "admin"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Confirme a PO");
    }

    @Test
    void newVersionGetsPreviousAutomaticallyWithoutInheritingConfirmation() {
        scenario.accountMapping.loadSheet(scenario.condominiumId, budget.getId(), null,
                "1621;1.7.8\n1324;AJUSTE (estorno)\n",
                "admin");
        scenario.accountMapping.batch(scenario.condominiumId, budget.getId(),
                new AccountMappingBatchRequest(AccountMappingBatchAction.CONFIRM, List.of("1621", "1324")),
                "admin");
        Budget v2 = scenario.readBudget(PilotBudget.defaults());
        var request = scenario.pilotRequest(v2);
        scenario.confirmation.confirm(scenario.condominiumId, v2.getId(), new BudgetConfirmationRequest("2026-10",
                "2027-04",
                request.minutesFileId(), false, request.approvalDate(), request.effectiveCodes(), request.funds(), true,
                false, null), "admin");

        var r = scenario.accountMapping.suggest(scenario.condominiumId, v2.getId(), "admin");

        assertThat(r.fromPreviousVersion()).isEqualTo(2);
        var same = scenario.accountMapping.list(scenario.condominiumId, v2.getId(),
                AccountMappingFilter.SAME_AS_PREVIOUS_VERSION);
        assertThat(same.accounts()).extracting(AccountMappingResponse::account).containsExactly("1324", "1621");
        assertThat(same.accounts()).allMatch(c -> c.status() == AccountMappingStatus.AUTOMATIC
                && c.source() == AccountMappingSource.PREVIOUS_VERSION && c.reason().startsWith("igual à versão anterior"));
        assertThat(same.accounts().get(1).target().lineId()).isEqualTo(scenario.line(v2, "1.7.8", 0).getId());
        // Version 1 does not change; version 2 does not inherit the confirmation, but its automatic links count
        assertThat(EffectiveAccountMapping.effective(scenario.mappings.stream()
                .filter(d -> d.getBudgetId().equals(budget.getId())).toList())).containsOnlyKeys("1324", "1621");
        assertThat(scenario.mappings.stream().filter(d -> d.getBudgetId().equals(v2.getId())))
                .noneMatch(d -> d.getStatus() == AccountMappingStatus.CONFIRMED);
        assertThat(EffectiveAccountMapping.effective(scenario.mappings.stream()
                .filter(d -> d.getBudgetId().equals(v2.getId())).toList())).containsKeys("1324", "1621");

        scenario.accountMapping.batch(scenario.condominiumId, v2.getId(),
                new AccountMappingBatchRequest(AccountMappingBatchAction.CONFIRM,
                        same.accounts().stream().map(AccountMappingResponse::account).toList()),
                "admin");
        assertThat(scenario.accountMapping.list(scenario.condominiumId, v2.getId(),
                null).summary().confirmed()).isEqualTo(2);
    }

    @Test
    void openingTheScreenSuggestsByItselfAndAgainChangesNothing() {
        scenario.published.clear();
        var list = scenario.accountMapping.open(scenario.condominiumId, budget.getId(), AccountMappingFilter.ALL);

        // Q51, Q54: no click; the link is AUTOMATIC, authored by the system, and already counts
        assertThat(list.summary()).isEqualTo(new AccountMappingSummaryResponse(3, 1, 0, 0, 0, 2));
        assertThat(list.automaticBySource().name()).isEqualTo(1);
        assertThat(single("1621").getUpdatedBy()).isEqualTo(AccountMappingService.SYSTEM);
        assertThat(EffectiveAccountMapping.effective(scenario.mappings)).containsOnlyKeys("1621");
        assertThat(scenario.published).isEmpty();

        // Rejection and the Admin's choice are never undone by a new generation
        scenario.accountMapping.batch(scenario.condominiumId, budget.getId(),
                new AccountMappingBatchRequest(AccountMappingBatchAction.REJECT, List.of("1621")), "admin");
        scenario.accountMapping.setTarget(scenario.condominiumId, budget.getId(), "1108",
                new MappingTargetRequest(MappingTargetType.BUDGET_LINE, scenario.line(budget, "1.3.20", 0).getId(), null,
                        false), "admin");
        int events = scenario.mappingEvents.size();
        scenario.accountMapping.open(scenario.condominiumId, budget.getId(), AccountMappingFilter.ALL);
        assertThat(scenario.accountMapping.suggestPending(scenario.condominiumId)).isZero();

        assertThat(scenario.mappingEvents).hasSize(events);
        assertThat(single("1621").getStatus()).isEqualTo(AccountMappingStatus.REJECTED);
        assertThat(single("1108").getSource()).isEqualTo(AccountMappingSource.ADMIN);
        assertThat(single("1108").getBudgetLineId()).isEqualTo(scenario.line(budget, "1.3.20", 0).getId());
    }

    @Test
    void storedSheetIsAppliedToNewAccountsAndToNewVersions() {
        scenario.accountMapping.loadSheet(scenario.condominiumId, budget.getId(), "mapa.csv",
                "1621;1.7.8\n1108;1.3.20\n1324;AJUSTE (estorno)\n4444;1.8.1\n", "admin");
        assertThat(scenario.sheetItems).hasSize(4).allMatch(i -> i.getFileSha256().length() == 64
                && i.getFileName().equals("mapa.csv"));
        var stored = scenario.accountMapping.list(scenario.condominiumId, budget.getId(), null).sheet();
        assertThat(stored.lines()).isEqualTo(4);
        assertThat(stored.loadedBy()).isEqualTo("admin");

        // An account that shows up later in a cash flow takes the stored sheet's target, AUTOMATIC
        scenario.debit("4444", "MATERIAL DE LIMPEZA", "100.00", LocalDate.of(2026, 9, 12));
        scenario.debit("5555", "CONTA NOVA SEM NOME CONHECIDO", "50.00", LocalDate.of(2026, 9, 12));
        scenario.accountMapping.suggestPending(scenario.condominiumId);
        AccountMapping cleaning = single("4444");
        assertThat(cleaning.getSource()).isEqualTo(AccountMappingSource.SPREADSHEET);
        assertThat(cleaning.getStatus()).isEqualTo(AccountMappingStatus.AUTOMATIC);
        assertThat(cleaning.getBudgetLineId()).isEqualTo(scenario.line(budget, "1.8.1", 0).getId());

        // A new sheet replaces the stored one
        scenario.accountMapping.loadSheet(scenario.condominiumId, budget.getId(), "mapa2.csv", "1621;1.7.8\n", "admin");
        assertThat(scenario.sheetItems).singleElement().satisfies(i -> assertThat(i.getFileName()).isEqualTo("mapa2.csv"));

        // New version: previous version first (only the confirmed 1621), then the stored sheet, then the name
        scenario.accountMapping.loadSheet(scenario.condominiumId, budget.getId(), "mapa3.csv",
                "1621;1.3.20\n1108;1.3.20\n", "admin");
        scenario.accountMapping.batch(scenario.condominiumId, budget.getId(),
                new AccountMappingBatchRequest(AccountMappingBatchAction.CONFIRM, List.of("1621")), "admin");
        Budget v2 = secondVersion();
        assertThat(scenario.accountMapping.suggestPending(scenario.condominiumId)).isPositive();

        assertThat(single(v2, "1621").getSource()).isEqualTo(AccountMappingSource.PREVIOUS_VERSION);
        assertThat(single(v2, "1108").getSource()).isEqualTo(AccountMappingSource.SPREADSHEET);
        assertThat(single(v2, "1108").getBudgetLineId()).isEqualTo(scenario.line(v2, "1.3.20", 0).getId());
        assertThat(scenario.mappings.stream().filter(d -> d.getBudgetId().equals(v2.getId())))
                .allMatch(d -> d.getStatus() == AccountMappingStatus.AUTOMATIC);
    }

    @Test
    void confirmAllSuggestionsNeedsTheCountTheScreenShowed() {
        scenario.accountMapping.loadSheet(scenario.condominiumId, budget.getId(), null,
                "1621;1.7.8\n1108;1.3.20\n1324;AJUSTE (estorno)\n", "admin");
        scenario.accountMapping.batch(scenario.condominiumId, budget.getId(),
                new AccountMappingBatchRequest(AccountMappingBatchAction.REJECT, List.of("1324")), "admin");
        var screen = scenario.accountMapping.list(scenario.condominiumId, budget.getId(), null);
        assertThat(screen.automaticBySource().spreadsheet()).isEqualTo(2);
        int events = scenario.mappingEvents.size();
        scenario.published.clear();

        assertThatThrownBy(() -> scenario.accountMapping.confirmAllSuggested(scenario.condominiumId, budget.getId(), 3,
                "admin")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("mudaram");
        assertThat(scenario.mappingEvents).hasSize(events);

        var r = scenario.accountMapping.confirmAllSuggested(scenario.condominiumId, budget.getId(), 2, "admin");

        assertThat(r.changed()).isEqualTo(2);
        assertThat(EffectiveAccountMapping.effective(scenario.mappings)).containsOnlyKeys("1108", "1621");
        assertThat(scenario.mappingEvents.subList(events, scenario.mappingEvents.size())).hasSize(2)
                .allMatch(e -> e.getAction() == AccountMappingAction.CONFIRMED && e.getUsername().equals("admin"));
        assertThat(single("1324").getStatus()).isEqualTo(AccountMappingStatus.REJECTED);
        assertThat(scenario.published).hasSize(1);
    }

    private Budget secondVersion() {
        Budget v2 = scenario.readBudget(PilotBudget.defaults());
        var request = scenario.pilotRequest(v2);
        scenario.confirmation.confirm(scenario.condominiumId, v2.getId(), new BudgetConfirmationRequest("2026-10",
                "2027-04", request.minutesFileId(), false, request.approvalDate(), request.effectiveCodes(),
                request.funds(), true, false, null), "admin");
        return v2;
    }

    private AccountMapping single(Budget b, String account) {
        return scenario.mappings.stream().filter(d -> d.getBudgetId().equals(b.getId())
                && d.getAccountCode().equals(account)).findFirst().orElseThrow();
    }

    private AccountMapping single(String account) {
        return scenario.mappings.stream().filter(d -> d.getAccountCode().equals(account)).findFirst().orElseThrow();
    }
}
