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
import br.com.condominioauditoria.api.orcamento.CenarioPo;
import br.com.condominioauditoria.api.orcamento.PoDoPiloto;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** RF-11.7 and ADR 0005, Decision 1: item catalog, suggestion by budget account and group, confirmation and trail. */
class BudgetItemServiceTest {

    private final CenarioPo scenario = new CenarioPo();

    @Test
    void firstConfirmedBudgetBecomesConfirmedCatalog() {
        Budget a = scenario.poConfirmada();

        long budgetLines = scenario.linhas.stream()
                .filter(l -> l.getBudgetId().equals(a.getId()) && l.getType() == BudgetLineType.LINHA).count();
        assertThat(scenario.rubricas).hasSize((int) budgetLines);
        assertThat(scenario.linhasRubrica).hasSize((int) budgetLines).allSatisfy(lineItem -> {
            assertThat(lineItem.getStatus()).isEqualTo(BudgetItemStatus.CONFIRMADO);
            assertThat(lineItem.getSource()).isEqualTo(BudgetItemSource.PRIMEIRA_PO);
        });
        assertThat(scenario.eventosRubrica).hasSize((int) budgetLines);
        assertThat(itemOf(a, "1.3.20", 0).getName()).isEqualTo("1682 - Sindicatura Profissional");
        assertThat(itemOf(a, "1.3.20", 0).getGroupCode()).isEqualTo("1.3");
        // Fund (1.9) also becomes an item; a line without account uses the account column text, otherwise the
        // description
        assertThat(itemOf(a, "1.9.1", 0).getName()).isEqualTo("Fundo de Reserva");
        assertThat(itemOf(a, "1.4.1", 0).getName()).isEqualTo("Força e Luz");
        var list = scenario.servicoRubricas.list(scenario.condominioId, a.getId(), null);
        assertThat(list.summary().confirmed()).isEqualTo((int) budgetLines);
        assertThat(list.summary().withoutItem()).isZero();
    }

    @Test
    void itemFromOtherFiscalYearIsSuggestedWithReasonAndOnlyCountsOnceConfirmed() {
        Budget a = scenario.poConfirmada();
        int itemsOfA = scenario.rubricas.size();
        Budget b = nextFiscalYear(PoDoPiloto.padrao());

        BudgetLineItem lineItem = link(b, "1.3.20", 0);
        assertThat(lineItem.getStatus()).isEqualTo(BudgetItemStatus.SUGERIDO);
        assertThat(lineItem.getSource()).isEqualTo(BudgetItemSource.CONTA_PO);
        assertThat(lineItem.getReason())
                .isEqualTo("mesma conta da PO e mesmo grupo: 1682 - Sindicatura Profissional, 1.3");
        assertThat(lineItem.getBudgetItemId()).isEqualTo(link(a, "1.3.20", 0).getBudgetItemId());
        // Suggestion neither creates an item nor confirms anything
        assertThat(scenario.rubricas).hasSize(itemsOfA);
        var pending = scenario.servicoRubricas.list(scenario.condominioId, b.getId(), BudgetItemFilter.CONFIRMADO);
        assertThat(pending.lines()).isEmpty();
    }

    @Test
    void pumpsAndWaterTankAreSuggestedSeparately() {
        Budget a = scenario.poConfirmada();
        Budget b = nextFiscalYear(PoDoPiloto.padrao());

        BudgetLineItem pumps = link(b, "1.3.2", 0);
        BudgetLineItem waterTank = link(b, "1.3.25", 0);
        assertThat(pumps.getBudgetItemId()).isEqualTo(link(a, "1.3.2", 0).getBudgetItemId());
        assertThat(waterTank.getBudgetItemId()).isEqualTo(link(a, "1.3.25", 0).getBudgetItemId());
        assertThat(pumps.getBudgetItemId()).isNotEqualTo(waterTank.getBudgetItemId());
        assertThat(waterTank.getReason()).isEqualTo("mesma conta da PO e mesmo grupo: 1624 - Caixa D'água, 1.3");
    }

    @Test
    void account1606InTwoGroupsGoesToDifferentItems() {
        Budget a = scenario.lerPo(PoDoPiloto.padrao().comAparelhosDeGinastica());
        scenario.confirmacao.confirm(scenario.condominioId, a.getId(), scenario.pedidoDoPiloto(a), "admin");
        Budget b = nextFiscalYear(PoDoPiloto.padrao().comAparelhosDeGinastica());

        BudgetLineItem inContracts = link(b, "1.3.5", 0);
        BudgetLineItem inMaterials = link(b, "1.7.2", 0);
        assertThat(inContracts.getBudgetItemId()).isEqualTo(link(a, "1.3.5", 0).getBudgetItemId());
        assertThat(inMaterials.getBudgetItemId()).isEqualTo(link(a, "1.7.2", 0).getBudgetItemId());
        assertThat(inContracts.getBudgetItemId()).isNotEqualTo(inMaterials.getBudgetItemId());
        assertThat(inMaterials.getReason()).isEqualTo("mesma conta da PO e mesmo grupo: 1606 - Aparelhos de Ginástica, 1.7");
    }

    @Test
    void batchSavesOneEventPerLineAndSkipsUnchanged() {
        scenario.poConfirmada();
        Budget b = nextFiscalYear(PoDoPiloto.padrao());
        List<UUID> suggested = scenario.linhasRubrica.stream()
                .filter(lineItem -> lineItem.getBudgetId().equals(b.getId()) && lineItem.getStatus() == BudgetItemStatus.SUGERIDO)
                .map(BudgetLineItem::getBudgetLineId).toList();
        assertThat(suggested).isNotEmpty();
        int eventsBefore = scenario.eventosRubrica.size();

        var r = scenario.servicoRubricas.batch(scenario.condominioId, b.getId(),
                new BudgetItemBatchRequest(BudgetItemBatchAction.CONFIRMAR, suggested), "admin");

        assertThat(r.changed()).isEqualTo(suggested.size());
        assertThat(r.skipped()).isEmpty();
        assertThat(scenario.eventosRubrica.subList(eventsBefore, scenario.eventosRubrica.size()))
                .hasSize(suggested.size())
                .allSatisfy(e -> {
                    assertThat(e.getAction()).isEqualTo(BudgetItemAction.CONFIRMADO);
                    assertThat(e.getPreviousStatus()).isEqualTo(BudgetItemStatus.SUGERIDO);
                    assertThat(e.getUsername()).isEqualTo("admin");
                });
        var again = scenario.servicoRubricas.batch(scenario.condominioId, b.getId(),
                new BudgetItemBatchRequest(BudgetItemBatchAction.CONFIRMAR, suggested.subList(0, 1)), "admin");
        assertThat(again.changed()).isZero();
        assertThat(again.skipped().getFirst().reason()).isEqualTo("já está confirmado");
    }

    @Test
    void adminChangesItemOrCreatesNewOneFromLine() {
        Budget a = scenario.poConfirmada();
        Budget b = nextFiscalYear(PoDoPiloto.padrao());
        UUID waterTankLine = scenario.linha(b, "1.3.2", 1).getId();
        UUID pumps = link(a, "1.3.2", 0).getBudgetItemId();

        var changed = scenario.servicoRubricas.setItem(scenario.condominioId, b.getId(), waterTankLine,
                new LineBudgetItemRequest(pumps, null, null), "admin");
        assertThat(changed.status()).isEqualTo(BudgetItemStatus.CONFIRMADO);
        assertThat(changed.source()).isEqualTo(BudgetItemSource.MANUAL);
        assertThat(changed.budgetItem().id()).isEqualTo(pumps);
        assertThat(scenario.eventosRubrica.getLast().getAction()).isEqualTo(BudgetItemAction.ALTERADO);
        assertThat(scenario.eventosRubrica.getLast().getPreviousItem()).isEqualTo("1624 - Caixa D'água");

        int itemsBefore = scenario.rubricas.size();
        var created = scenario.servicoRubricas.setItem(scenario.condominioId, b.getId(), waterTankLine,
                new LineBudgetItemRequest(null, "Caixas d'água (limpeza)", true), "admin");
        assertThat(scenario.rubricas).hasSize(itemsBefore + 1);
        assertThat(created.budgetItem().name()).isEqualTo("Caixas d'água (limpeza)");
        assertThat(created.budgetItem().group()).isEqualTo("1.3");
        var lastOnes = scenario.eventosRubrica.subList(scenario.eventosRubrica.size() - 2,
                scenario.eventosRubrica.size());
        assertThat(lastOnes).extracting(BudgetItemEvent::getAction)
                .containsExactly(BudgetItemAction.CRIADA, BudgetItemAction.ALTERADO);

        assertThatThrownBy(() -> scenario.servicoRubricas.setItem(scenario.condominioId, b.getId(), waterTankLine,
                new LineBudgetItemRequest(pumps, "outra", null), "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));
    }

    @Test
    void reapprovalInheritsItemOfSameLineFromPreviousVersion() {
        Budget a = scenario.poConfirmada();
        Budget second = scenario.lerPo(PoDoPiloto.padrao());
        var p = scenario.pedidoDoPiloto(second);
        scenario.confirmacao.confirm(scenario.condominioId, second.getId(), new BudgetConfirmationRequest("2026-09",
                "2027-04",
                p.minutesFileId(), false, LocalDate.of(2026, 8, 30), p.effectiveCodes(), p.funds(), true, false, null),
                "admin");

        BudgetLineItem lineItem = link(second, "1.3.20", 0);
        assertThat(lineItem.getSource()).isEqualTo(BudgetItemSource.VERSAO_ANTERIOR);
        assertThat(lineItem.getStatus()).isEqualTo(BudgetItemStatus.SUGERIDO);
        assertThat(lineItem.getReason()).isEqualTo("linha igual na versão anterior: 1.3.20 1682 - Sindicatura Profissional");
        assertThat(lineItem.getBudgetItemId()).isEqualTo(link(a, "1.3.20", 0).getBudgetItemId());
    }

    @Test
    void suggestionsDoNotTouchLineThatAlreadyHasItem() {
        scenario.poConfirmada();
        Budget b = nextFiscalYear(PoDoPiloto.padrao());
        int linked = scenario.linhasRubrica.size();
        int events = scenario.eventosRubrica.size();

        var r = scenario.servicoRubricas.suggest(scenario.condominioId, b.getId(), "admin");

        assertThat(r.firstBudget()).isFalse();
        assertThat(r.suggested()).isZero();
        assertThat(scenario.linhasRubrica).hasSize(linked);
        assertThat(scenario.eventosRubrica).hasSize(events);
    }

    @Test
    void budgetConfirmedBeforeItemsBuildsCatalogFromSuggestions() {
        Budget a = scenario.poConfirmada();
        scenario.rubricas.clear();
        scenario.linhasRubrica.clear();

        var r = scenario.servicoRubricas.suggest(scenario.condominioId, a.getId(), "admin");

        assertThat(r.firstBudget()).isTrue();
        assertThat(r.createdItems()).isEqualTo(scenario.rubricas.size()).isPositive();
    }

    @Test
    void unconfirmedBudgetGetsNoItem() {
        Budget read = scenario.lerPo(PoDoPiloto.padrao());

        assertThatThrownBy(() -> scenario.servicoRubricas.suggest(scenario.condominioId, read.getId(), "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(scenario.rubricas).isEmpty();
    }

    @Test
    void creatingAndRenamingItemGoToTrail() {
        var created = scenario.servicoRubricas.create(scenario.condominioId, new NewBudgetItemRequest(" Academia ",
                "1.3"),
                "admin");
        var renamed = scenario.servicoRubricas.rename(scenario.condominioId, created.id(),
                new RenameBudgetItemRequest("Academia e ginástica"), "admin");

        assertThat(created.name()).isEqualTo("Academia");
        assertThat(renamed.name()).isEqualTo("Academia e ginástica");
        assertThat(scenario.eventosRubrica).extracting(BudgetItemEvent::getAction)
                .containsExactly(BudgetItemAction.CRIADA, BudgetItemAction.RENOMEADA);
        assertThat(scenario.eventosRubrica.getLast().getPreviousItem()).isEqualTo("Academia");
        assertThatThrownBy(() -> scenario.servicoRubricas.create(scenario.condominioId, new NewBudgetItemRequest(" ",
                null),
                "admin")).isInstanceOf(ResponseStatusException.class);
    }

    /**
     * Reads and confirms the budget as if it belonged to the next fiscal year (05/2027 to 04/2028), with 1.3.25 and the
     * funds.
     */
    private Budget nextFiscalYear(PoDoPiloto budget) {
        Budget b = scenario.lerPo(budget);
        var p = scenario.pedidoDoPiloto(b);
        scenario.confirmacao.confirm(scenario.condominioId, b.getId(), new BudgetConfirmationRequest("2027-05",
                "2028-04",
                p.minutesFileId(), false, LocalDate.of(2027, 5, 20), p.effectiveCodes(), p.funds(), false, false, null),
                "admin");
        return b;
    }

    /** Item of the line by effective code (the index separates repeated codes). */
    private BudgetLineItem link(Budget budget, String effectiveCode, int index) {
        UUID line = scenario.linhas.stream()
                .filter(l -> l.getBudgetId().equals(budget.getId()) && l.getEffectiveCode().equals(effectiveCode))
                .sorted(java.util.Comparator.comparingInt(BudgetLine::getPosition)).toList().get(index).getId();
        return scenario.linhasRubrica.stream().filter(lineItem -> lineItem.getBudgetLineId().equals(line)).findFirst().orElseThrow();
    }

    private BudgetItem itemOf(Budget budget, String effectiveCode, int index) {
        UUID id = link(budget, effectiveCode, index).getBudgetItemId();
        return scenario.rubricas.stream().filter(r -> r.getId().equals(id)).findFirst().orElseThrow();
    }
}
