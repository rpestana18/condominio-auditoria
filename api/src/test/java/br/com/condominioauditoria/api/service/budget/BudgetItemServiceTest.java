package br.com.condominioauditoria.api.service.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.condominioauditoria.api.dto.request.budget.BudgetConfirmationRequest;
import br.com.condominioauditoria.api.dto.request.budget.BudgetItemBatchRequest;
import br.com.condominioauditoria.api.dto.request.budget.LineBudgetItemRequest;
import br.com.condominioauditoria.api.dto.request.budget.NewBudgetItemRequest;
import br.com.condominioauditoria.api.dto.request.budget.RenameBudgetItemRequest;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetItem;
import br.com.condominioauditoria.api.model.budget.BudgetItemEvent;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.budget.BudgetLineItem;
import br.com.condominioauditoria.api.model.enums.BudgetItemAction;
import br.com.condominioauditoria.api.model.enums.BudgetItemBatchAction;
import br.com.condominioauditoria.api.model.enums.BudgetItemFilter;
import br.com.condominioauditoria.api.model.enums.BudgetItemSource;
import br.com.condominioauditoria.api.model.enums.BudgetItemStatus;
import br.com.condominioauditoria.api.model.enums.BudgetLineType;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** RF-11.7 and ADR 0005, Decision 1: item catalog, suggestion by budget account and group, confirmation and trail. */
class BudgetItemServiceTest {

    private final BudgetScenario scenario = new BudgetScenario();

    @Test
    void firstConfirmedBudgetBecomesConfirmedCatalog() {
        Budget a = scenario.confirmedBudget();

        long budgetLines = scenario.lines.stream()
                .filter(l -> l.getBudgetId().equals(a.getId()) && l.getType() == BudgetLineType.LINE).count();
        assertThat(scenario.budgetItems).hasSize((int) budgetLines);
        assertThat(scenario.lineItems).hasSize((int) budgetLines).allSatisfy(lineItem -> {
            assertThat(lineItem.getStatus()).isEqualTo(BudgetItemStatus.CONFIRMED);
            assertThat(lineItem.getSource()).isEqualTo(BudgetItemSource.FIRST_BUDGET);
        });
        assertThat(scenario.budgetItemEvents).hasSize((int) budgetLines);
        assertThat(itemOf(a, "1.3.20", 0).getName()).isEqualTo("1682 - Sindicatura Profissional");
        assertThat(itemOf(a, "1.3.20", 0).getGroupCode()).isEqualTo("1.3");
        // Fund (1.9) also becomes an item; a line without account uses the account column text, otherwise the
        // description
        assertThat(itemOf(a, "1.9.1", 0).getName()).isEqualTo("Fundo de Reserva");
        assertThat(itemOf(a, "1.4.1", 0).getName()).isEqualTo("Força e Luz");
        var list = scenario.budgetItemService.list(scenario.condominiumId, a.getId(), null);
        assertThat(list.summary().confirmed()).isEqualTo((int) budgetLines);
        assertThat(list.summary().withoutItem()).isZero();
    }

    @Test
    void itemFromOtherFiscalYearIsSuggestedWithReasonAndOnlyCountsOnceConfirmed() {
        Budget a = scenario.confirmedBudget();
        int itemsOfA = scenario.budgetItems.size();
        Budget b = nextFiscalYear(PilotBudget.defaults());

        BudgetLineItem lineItem = link(b, "1.3.20", 0);
        assertThat(lineItem.getStatus()).isEqualTo(BudgetItemStatus.SUGGESTED);
        assertThat(lineItem.getSource()).isEqualTo(BudgetItemSource.BUDGET_ACCOUNT);
        assertThat(lineItem.getReason())
                .isEqualTo("mesma conta da PO e mesmo grupo: 1682 - Sindicatura Profissional, 1.3");
        assertThat(lineItem.getBudgetItemId()).isEqualTo(link(a, "1.3.20", 0).getBudgetItemId());
        // Suggestion neither creates an item nor confirms anything
        assertThat(scenario.budgetItems).hasSize(itemsOfA);
        var pending = scenario.budgetItemService.list(scenario.condominiumId, b.getId(), BudgetItemFilter.CONFIRMED);
        assertThat(pending.lines()).isEmpty();
    }

    @Test
    void pumpsAndWaterTankAreSuggestedSeparately() {
        Budget a = scenario.confirmedBudget();
        Budget b = nextFiscalYear(PilotBudget.defaults());

        BudgetLineItem pumps = link(b, "1.3.2", 0);
        BudgetLineItem waterTank = link(b, "1.3.25", 0);
        assertThat(pumps.getBudgetItemId()).isEqualTo(link(a, "1.3.2", 0).getBudgetItemId());
        assertThat(waterTank.getBudgetItemId()).isEqualTo(link(a, "1.3.25", 0).getBudgetItemId());
        assertThat(pumps.getBudgetItemId()).isNotEqualTo(waterTank.getBudgetItemId());
        assertThat(waterTank.getReason()).isEqualTo("mesma conta da PO e mesmo grupo: 1624 - Caixa D'água, 1.3");
    }

    @Test
    void account1606InTwoGroupsGoesToDifferentItems() {
        Budget a = scenario.readBudget(PilotBudget.defaults().withGymEquipment());
        scenario.confirmation.confirm(scenario.condominiumId, a.getId(), scenario.pilotRequest(a), "admin");
        Budget b = nextFiscalYear(PilotBudget.defaults().withGymEquipment());

        BudgetLineItem inContracts = link(b, "1.3.5", 0);
        BudgetLineItem inMaterials = link(b, "1.7.2", 0);
        assertThat(inContracts.getBudgetItemId()).isEqualTo(link(a, "1.3.5", 0).getBudgetItemId());
        assertThat(inMaterials.getBudgetItemId()).isEqualTo(link(a, "1.7.2", 0).getBudgetItemId());
        assertThat(inContracts.getBudgetItemId()).isNotEqualTo(inMaterials.getBudgetItemId());
        assertThat(inMaterials.getReason()).isEqualTo("mesma conta da PO e mesmo grupo: 1606 - Aparelhos de Ginástica, 1.7");
    }

    @Test
    void batchSavesOneEventPerLineAndSkipsUnchanged() {
        scenario.confirmedBudget();
        Budget b = nextFiscalYear(PilotBudget.defaults());
        List<UUID> suggested = scenario.lineItems.stream()
                .filter(lineItem -> lineItem.getBudgetId().equals(b.getId()) && lineItem.getStatus() == BudgetItemStatus.SUGGESTED)
                .map(BudgetLineItem::getBudgetLineId).toList();
        assertThat(suggested).isNotEmpty();
        int eventsBefore = scenario.budgetItemEvents.size();

        var r = scenario.budgetItemService.batch(scenario.condominiumId, b.getId(),
                new BudgetItemBatchRequest(BudgetItemBatchAction.CONFIRM, suggested), "admin");

        assertThat(r.changed()).isEqualTo(suggested.size());
        assertThat(r.skipped()).isEmpty();
        assertThat(scenario.budgetItemEvents.subList(eventsBefore, scenario.budgetItemEvents.size()))
                .hasSize(suggested.size())
                .allSatisfy(e -> {
                    assertThat(e.getAction()).isEqualTo(BudgetItemAction.CONFIRMED);
                    assertThat(e.getPreviousStatus()).isEqualTo(BudgetItemStatus.SUGGESTED);
                    assertThat(e.getUsername()).isEqualTo("admin");
                });
        var again = scenario.budgetItemService.batch(scenario.condominiumId, b.getId(),
                new BudgetItemBatchRequest(BudgetItemBatchAction.CONFIRM, suggested.subList(0, 1)), "admin");
        assertThat(again.changed()).isZero();
        assertThat(again.skipped().getFirst().reason()).isEqualTo("já está confirmado");
    }

    @Test
    void adminChangesItemOrCreatesNewOneFromLine() {
        Budget a = scenario.confirmedBudget();
        Budget b = nextFiscalYear(PilotBudget.defaults());
        UUID waterTankLine = scenario.line(b, "1.3.2", 1).getId();
        UUID pumps = link(a, "1.3.2", 0).getBudgetItemId();

        var changed = scenario.budgetItemService.setItem(scenario.condominiumId, b.getId(), waterTankLine,
                new LineBudgetItemRequest(pumps, null, null), "admin");
        assertThat(changed.status()).isEqualTo(BudgetItemStatus.CONFIRMED);
        assertThat(changed.source()).isEqualTo(BudgetItemSource.MANUAL);
        assertThat(changed.budgetItem().id()).isEqualTo(pumps);
        assertThat(scenario.budgetItemEvents.getLast().getAction()).isEqualTo(BudgetItemAction.CHANGED);
        assertThat(scenario.budgetItemEvents.getLast().getPreviousItem()).isEqualTo("1624 - Caixa D'água");

        int itemsBefore = scenario.budgetItems.size();
        var created = scenario.budgetItemService.setItem(scenario.condominiumId, b.getId(), waterTankLine,
                new LineBudgetItemRequest(null, "Caixas d'água (limpeza)", true), "admin");
        assertThat(scenario.budgetItems).hasSize(itemsBefore + 1);
        assertThat(created.budgetItem().name()).isEqualTo("Caixas d'água (limpeza)");
        assertThat(created.budgetItem().group()).isEqualTo("1.3");
        var lastOnes = scenario.budgetItemEvents.subList(scenario.budgetItemEvents.size() - 2,
                scenario.budgetItemEvents.size());
        assertThat(lastOnes).extracting(BudgetItemEvent::getAction)
                .containsExactly(BudgetItemAction.CREATED, BudgetItemAction.CHANGED);

        assertThatThrownBy(() -> scenario.budgetItemService.setItem(scenario.condominiumId, b.getId(), waterTankLine,
                new LineBudgetItemRequest(pumps, "outra", null), "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));
    }

    @Test
    void reapprovalInheritsItemOfSameLineFromPreviousVersion() {
        Budget a = scenario.confirmedBudget();
        Budget second = scenario.readBudget(PilotBudget.defaults());
        var p = scenario.pilotRequest(second);
        scenario.confirmation.confirm(scenario.condominiumId, second.getId(), new BudgetConfirmationRequest("2026-09",
                "2027-04",
                p.minutesFileId(), false, LocalDate.of(2026, 8, 30), p.effectiveCodes(), p.funds(), true, false, null),
                "admin");

        BudgetLineItem lineItem = link(second, "1.3.20", 0);
        assertThat(lineItem.getSource()).isEqualTo(BudgetItemSource.PREVIOUS_VERSION);
        assertThat(lineItem.getStatus()).isEqualTo(BudgetItemStatus.SUGGESTED);
        assertThat(lineItem.getReason()).isEqualTo("linha igual na versão anterior: 1.3.20 1682 - Sindicatura Profissional");
        assertThat(lineItem.getBudgetItemId()).isEqualTo(link(a, "1.3.20", 0).getBudgetItemId());
    }

    @Test
    void suggestionsDoNotTouchLineThatAlreadyHasItem() {
        scenario.confirmedBudget();
        Budget b = nextFiscalYear(PilotBudget.defaults());
        int linked = scenario.lineItems.size();
        int events = scenario.budgetItemEvents.size();

        var r = scenario.budgetItemService.suggest(scenario.condominiumId, b.getId(), "admin");

        assertThat(r.firstBudget()).isFalse();
        assertThat(r.suggested()).isZero();
        assertThat(scenario.lineItems).hasSize(linked);
        assertThat(scenario.budgetItemEvents).hasSize(events);
    }

    @Test
    void budgetConfirmedBeforeItemsBuildsCatalogFromSuggestions() {
        Budget a = scenario.confirmedBudget();
        scenario.budgetItems.clear();
        scenario.lineItems.clear();

        var r = scenario.budgetItemService.suggest(scenario.condominiumId, a.getId(), "admin");

        assertThat(r.firstBudget()).isTrue();
        assertThat(r.createdItems()).isEqualTo(scenario.budgetItems.size()).isPositive();
    }

    @Test
    void unconfirmedBudgetGetsNoItem() {
        Budget read = scenario.readBudget(PilotBudget.defaults());

        assertThatThrownBy(() -> scenario.budgetItemService.suggest(scenario.condominiumId, read.getId(), "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(scenario.budgetItems).isEmpty();
    }

    @Test
    void creatingAndRenamingItemGoToTrail() {
        var created = scenario.budgetItemService.create(scenario.condominiumId, new NewBudgetItemRequest(" Academia ",
                "1.3"),
                "admin");
        var renamed = scenario.budgetItemService.rename(scenario.condominiumId, created.id(),
                new RenameBudgetItemRequest("Academia e ginástica"), "admin");

        assertThat(created.name()).isEqualTo("Academia");
        assertThat(renamed.name()).isEqualTo("Academia e ginástica");
        assertThat(scenario.budgetItemEvents).extracting(BudgetItemEvent::getAction)
                .containsExactly(BudgetItemAction.CREATED, BudgetItemAction.RENAMED);
        assertThat(scenario.budgetItemEvents.getLast().getPreviousItem()).isEqualTo("Academia");
        assertThatThrownBy(() -> scenario.budgetItemService.create(scenario.condominiumId, new NewBudgetItemRequest(" ",
                null),
                "admin")).isInstanceOf(ResponseStatusException.class);
    }

    /**
     * Reads and confirms the budget as if it belonged to the next fiscal year (05/2027 to 04/2028), with 1.3.25 and the
     * funds.
     */
    private Budget nextFiscalYear(PilotBudget budget) {
        Budget b = scenario.readBudget(budget);
        var p = scenario.pilotRequest(b);
        scenario.confirmation.confirm(scenario.condominiumId, b.getId(), new BudgetConfirmationRequest("2027-05",
                "2028-04",
                p.minutesFileId(), false, LocalDate.of(2027, 5, 20), p.effectiveCodes(), p.funds(), false, false, null),
                "admin");
        return b;
    }

    /** Item of the line by effective code (the index separates repeated codes). */
    private BudgetLineItem link(Budget budget, String effectiveCode, int index) {
        UUID line = scenario.lines.stream()
                .filter(l -> l.getBudgetId().equals(budget.getId()) && l.getEffectiveCode().equals(effectiveCode))
                .sorted(java.util.Comparator.comparingInt(BudgetLine::getPosition)).toList().get(index).getId();
        return scenario.lineItems.stream().filter(lineItem -> lineItem.getBudgetLineId().equals(line)).findFirst().orElseThrow();
    }

    private BudgetItem itemOf(Budget budget, String effectiveCode, int index) {
        UUID id = link(budget, effectiveCode, index).getBudgetItemId();
        return scenario.budgetItems.stream().filter(r -> r.getId().equals(id)).findFirst().orElseThrow();
    }
}
