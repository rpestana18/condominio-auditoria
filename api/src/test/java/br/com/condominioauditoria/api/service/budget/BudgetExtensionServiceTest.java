package br.com.condominioauditoria.api.service.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.condominioauditoria.api.dto.request.budget.BudgetConfirmationRequest;
import br.com.condominioauditoria.api.dto.request.budget.BudgetExtensionRequest;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualWarningResponse;
import br.com.condominioauditoria.api.dto.response.budget.FiscalYearMonthResponse;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.Enrichment;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.LedgerEntryData;
import br.com.condominioauditoria.api.model.accounting.LedgerEntry;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetEvent;
import br.com.condominioauditoria.api.model.enums.BudgetVsActualStatus;
import br.com.condominioauditoria.api.model.file.SourceFile;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * RF-11.3 and ADR 0005, Decision 4: budget of the month with extension. Test fiscal year 04/2025 to 03/2026 (the pilot
 * budget confirmed with other dates) and the pilot's 2026/2027 budget (05/2026 to 04/2027): 04/2026 falls between the
 * two.
 */
class BudgetExtensionServiceTest {

    private static final YearMonth APRIL = YearMonth.of(2026, 4);

    private final BudgetScenario scenario = new BudgetScenario();

    @Test
    void monthBetweenTwoFiscalYearsHasNoApprovedBudget() {
        previous();
        scenario.confirmedBudget();

        assertThat(scenario.budgetOfMonth(APRIL)).isEmpty();
        BudgetVsActualResponse r = scenario.budgetVsActual.get(scenario.condominiumId, "2026-04", null);
        assertThat(r.status()).isEqualTo(BudgetVsActualStatus.SEM_PO);
        assertThat(r.message()).isEqualTo("Sem PO aprovada para 04/2026");
    }

    @Test
    void extendedUntilAprilUsesPreviousBudgetWithWarningAndGoesToTrail() {
        Budget previous = previous();
        scenario.confirmedBudget();

        var detail = scenario.extension.extend(scenario.condominiumId, previous.getId(),
                new BudgetExtensionRequest("2026-04", "PO 2026/2027 aprovada só na AGO de maio"), "admin");

        assertThat(detail.budget().extension().from()).isEqualTo("2026-04");
        assertThat(detail.budget().extension().until()).isEqualTo("2026-04");
        assertThat(scenario.budgetOfMonth(APRIL)).contains(previous);
        BudgetVsActualResponse r = scenario.budgetVsActual.get(scenario.condominiumId, "2026-04", null);
        assertThat(r.budget().id()).isEqualTo(previous.getId());
        assertThat(r.status()).isEqualTo(BudgetVsActualStatus.SEM_FLUXO);
        assertThat(r.months()).singleElement().extracting(FiscalYearMonthResponse::extended).isEqualTo(true);
        assertThat(r.warnings()).extracting(BudgetVsActualWarningResponse::code).contains("PO_PRORROGADA");
        assertThat(r.warnings().stream().filter(a -> a.code().equals("PO_PRORROGADA")).findFirst().orElseThrow().text())
                .isEqualTo("PO prorrogada: 04/2026 usa a PO do exercício 04/2025 a 03/2026, prorrogada até 04/2026 por"
                        + " admin. Justificativa: PO 2026/2027 aprovada só na AGO de maio");
        BudgetEvent e = scenario.budgetEvents.getLast();
        assertThat(e.getType()).isEqualTo(BudgetEvent.EXTENDED);
        assertThat(e.getUsername()).isEqualTo("admin");
        assertThat(e.getJustification()).isEqualTo("PO 2026/2027 aprovada só na AGO de maio");
        assertThat(scenario.published).isNotEmpty();
    }

    @Test
    void withoutJustificationOrOverMonthWithConfirmedBudgetIsRejected() {
        Budget previous = previous();
        scenario.confirmedBudget();

        assertThatThrownBy(() -> scenario.extension.extend(scenario.condominiumId, previous.getId(),
                new BudgetExtensionRequest("2026-04", "  "), "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));
        assertThatThrownBy(() -> scenario.extension.extend(scenario.condominiumId, previous.getId(),
                new BudgetExtensionRequest("2026-05", "atraso"), "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(e.getReason()).startsWith("05/2026 já tem PO confirmada");
                });
        assertThatThrownBy(() -> scenario.extension.extend(scenario.condominiumId, previous.getId(),
                new BudgetExtensionRequest("2026-03", "atraso"), "admin"))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(previous.getExtendedUntil()).isNull();
    }

    @Test
    void newConfirmedBudgetShortensExtension() {
        Budget previous = previous();
        scenario.extension.extend(scenario.condominiumId, previous.getId(),
                new BudgetExtensionRequest("2026-05", "assembleia adiada"), "admin");

        scenario.confirmedBudget();

        assertThat(previous.getExtendedUntil()).isEqualTo(APRIL);
        BudgetEvent e = scenario.budgetEvents.stream()
                .filter(x -> x.getType().equals(BudgetEvent.EXTENSION_SHORTENED)).findFirst().orElseThrow();
        assertThat(e.getBudgetId()).isEqualTo(previous.getId());
        assertThat(e.getJustification()).startsWith("PO 2026/2027 confirmada");
        assertThat(scenario.budgetOfMonth(YearMonth.of(2026, 5)).orElseThrow().getId())
                .isNotEqualTo(previous.getId());
    }

    @Test
    void newBudgetStartingRightAfterFiscalYearUndoesExtension() {
        Budget previous = previous();
        scenario.extension.extend(scenario.condominiumId, previous.getId(),
                new BudgetExtensionRequest("2026-05", "assembleia adiada"), "admin");
        Budget created = scenario.readBudget(PilotBudget.defaults());
        var p = scenario.pilotRequest(created);

        scenario.confirmation.confirm(scenario.condominiumId, created.getId(), new BudgetConfirmationRequest("2026-04",
                "2027-03",
                p.minutesFileId(), false, LocalDate.of(2026, 3, 30), p.effectiveCodes(), p.funds(), false, false, null),
                "admin");

        assertThat(previous.getExtendedUntil()).isNull();
        assertThat(scenario.budgetOfMonth(APRIL)).contains(created);
    }

    @Test
    void extendedMonthsStayOutOfAccumulated() {
        Budget previous = previous();
        scenario.extension.extend(scenario.condominiumId, previous.getId(),
                new BudgetExtensionRequest("2026-04", "assembleia adiada"), "admin");
        SourceFile march = scenario.cashFlow("fluxo-2026-03.pdf", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                1);
        SourceFile april = scenario.cashFlow("fluxo-2026-04.pdf", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30),
                1);
        debit(march, "100.00", LocalDate.of(2026, 3, 10));
        debit(april, "250.00", LocalDate.of(2026, 4, 10));

        BudgetVsActualResponse r = scenario.budgetVsActual.get(scenario.condominiumId, "acumulado",
                previous.getId());

        assertThat(r.months()).hasSize(13);
        assertThat(r.months().subList(0, 12)).noneMatch(FiscalYearMonthResponse::extended);
        FiscalYearMonthResponse last = r.months().getLast();
        assertThat(last.month()).isEqualTo("2026-04");
        assertThat(last.extended()).isTrue();
        assertThat(last.actualExpense()).isEqualByComparingTo("250.00");
        assertThat(r.summedMonths()).containsExactly("2026-03");
        assertThat(r.totals().actualExpense()).isEqualByComparingTo("100.00");
        assertThat(r.warnings()).extracting(BudgetVsActualWarningResponse::text).contains("PO prorrogada até 04/2026: abr/2026 aparece depois do"
                + " exercício, marcado \"prorrogado\", e não entra no acumulado.");

        BudgetVsActualResponse month = scenario.budgetVsActual.get(scenario.condominiumId, "2026-04", null);
        assertThat(month.totals().actualExpense()).isEqualByComparingTo("250.00");
    }

    @Test
    void undoGoesBackToNoBudgetAndGoesToTrail() {
        Budget previous = previous();
        scenario.extension.extend(scenario.condominiumId, previous.getId(),
                new BudgetExtensionRequest("2026-04", "assembleia adiada"), "admin");

        scenario.extension.undo(scenario.condominiumId, previous.getId(), "admin");

        assertThat(scenario.budgetOfMonth(APRIL)).isEmpty();
        assertThat(scenario.budgetEvents.getLast().getType()).isEqualTo(BudgetEvent.EXTENSION_UNDONE);
        assertThatThrownBy(() -> scenario.extension.undo(scenario.condominiumId, previous.getId(), "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    /** Budget of the test fiscal year 04/2025 to 03/2026 (the pilot's, confirmed with those dates). */
    private Budget previous() {
        Budget budget = scenario.readBudget(PilotBudget.defaults());
        var p = scenario.pilotRequest(budget);
        scenario.confirmation.confirm(scenario.condominiumId, budget.getId(), new BudgetConfirmationRequest("2025-04",
                "2026-03",
                p.minutesFileId(), false, LocalDate.of(2025, 3, 30), p.effectiveCodes(), p.funds(), false, false, null),
                "admin");
        return budget;
    }

    private void debit(SourceFile cashFlow, String amount, LocalDate date) {
        var read = new LedgerEntryData(1, scenario.ledgerEntries.size() + 1, date, "9999", "Teste", "", "Teste",
                BigDecimal.ZERO.setScale(2), new BigDecimal(amount), BigDecimal.ZERO.setScale(2),
                new Enrichment(null, null, null, false, false));
        scenario.ledgerEntries.add(new LedgerEntry(scenario.condominiumId, cashFlow.getId(),
                scenario.operatingFund.getId(),
                read));
    }
}
