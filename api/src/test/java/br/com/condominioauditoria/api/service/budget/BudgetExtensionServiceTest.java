package br.com.condominioauditoria.api.service.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.condominioauditoria.api.dto.request.budget.BudgetConfirmationRequest;
import br.com.condominioauditoria.api.dto.request.budget.BudgetExtensionRequest;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.Enrichment;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.LedgerEntryData;
import br.com.condominioauditoria.api.model.accounting.LedgerEntry;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetEvent;
import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.orcamento.CenarioPo;
import br.com.condominioauditoria.api.orcamento.PoDoPiloto;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.Aviso;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.MesExercicio;
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

    private final CenarioPo scenario = new CenarioPo();

    @Test
    void monthBetweenTwoFiscalYearsHasNoApprovedBudget() {
        previous();
        scenario.poConfirmada();

        assertThat(scenario.consultaVigente(APRIL)).isEmpty();
        PrevistoRealizado r = scenario.previstoRealizado.consultar(scenario.condominioId, "2026-04", null);
        assertThat(r.situacao()).isEqualTo(PrevistoRealizado.Situacao.SEM_PO);
        assertThat(r.mensagem()).isEqualTo("Sem PO aprovada para 04/2026");
    }

    @Test
    void extendedUntilAprilUsesPreviousBudgetWithWarningAndGoesToTrail() {
        Budget previous = previous();
        scenario.poConfirmada();

        var detail = scenario.prorrogacao.extend(scenario.condominioId, previous.getId(),
                new BudgetExtensionRequest("2026-04", "PO 2026/2027 aprovada só na AGO de maio"), "admin");

        assertThat(detail.budget().extension().from()).isEqualTo("2026-04");
        assertThat(detail.budget().extension().until()).isEqualTo("2026-04");
        assertThat(scenario.consultaVigente(APRIL)).contains(previous);
        PrevistoRealizado r = scenario.previstoRealizado.consultar(scenario.condominioId, "2026-04", null);
        assertThat(r.po().id()).isEqualTo(previous.getId());
        assertThat(r.situacao()).isEqualTo(PrevistoRealizado.Situacao.SEM_FLUXO);
        assertThat(r.meses()).singleElement().extracting(MesExercicio::prorrogado).isEqualTo(true);
        assertThat(r.avisos()).extracting(Aviso::codigo).contains("PO_PRORROGADA");
        assertThat(r.avisos().stream().filter(a -> a.codigo().equals("PO_PRORROGADA")).findFirst().orElseThrow().texto())
                .isEqualTo("PO prorrogada: 04/2026 usa a PO do exercício 04/2025 a 03/2026, prorrogada até 04/2026 por"
                        + " admin. Justificativa: PO 2026/2027 aprovada só na AGO de maio");
        BudgetEvent e = scenario.eventos.getLast();
        assertThat(e.getType()).isEqualTo(BudgetEvent.EXTENDED);
        assertThat(e.getUsername()).isEqualTo("admin");
        assertThat(e.getJustification()).isEqualTo("PO 2026/2027 aprovada só na AGO de maio");
        assertThat(scenario.publicados).isNotEmpty();
    }

    @Test
    void withoutJustificationOrOverMonthWithConfirmedBudgetIsRejected() {
        Budget previous = previous();
        scenario.poConfirmada();

        assertThatThrownBy(() -> scenario.prorrogacao.extend(scenario.condominioId, previous.getId(),
                new BudgetExtensionRequest("2026-04", "  "), "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));
        assertThatThrownBy(() -> scenario.prorrogacao.extend(scenario.condominioId, previous.getId(),
                new BudgetExtensionRequest("2026-05", "atraso"), "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(e.getReason()).startsWith("05/2026 já tem PO confirmada");
                });
        assertThatThrownBy(() -> scenario.prorrogacao.extend(scenario.condominioId, previous.getId(),
                new BudgetExtensionRequest("2026-03", "atraso"), "admin"))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(previous.getExtendedUntil()).isNull();
    }

    @Test
    void newConfirmedBudgetShortensExtension() {
        Budget previous = previous();
        scenario.prorrogacao.extend(scenario.condominioId, previous.getId(),
                new BudgetExtensionRequest("2026-05", "assembleia adiada"), "admin");

        scenario.poConfirmada();

        assertThat(previous.getExtendedUntil()).isEqualTo(APRIL);
        BudgetEvent e = scenario.eventos.stream()
                .filter(x -> x.getType().equals(BudgetEvent.EXTENSION_SHORTENED)).findFirst().orElseThrow();
        assertThat(e.getBudgetId()).isEqualTo(previous.getId());
        assertThat(e.getJustification()).startsWith("PO 2026/2027 confirmada");
        assertThat(scenario.consultaVigente(YearMonth.of(2026, 5)).orElseThrow().getId())
                .isNotEqualTo(previous.getId());
    }

    @Test
    void newBudgetStartingRightAfterFiscalYearUndoesExtension() {
        Budget previous = previous();
        scenario.prorrogacao.extend(scenario.condominioId, previous.getId(),
                new BudgetExtensionRequest("2026-05", "assembleia adiada"), "admin");
        Budget created = scenario.lerPo(PoDoPiloto.padrao());
        var p = scenario.pedidoDoPiloto(created);

        scenario.confirmacao.confirm(scenario.condominioId, created.getId(), new BudgetConfirmationRequest("2026-04",
                "2027-03",
                p.minutesFileId(), false, LocalDate.of(2026, 3, 30), p.effectiveCodes(), p.funds(), false, false, null),
                "admin");

        assertThat(previous.getExtendedUntil()).isNull();
        assertThat(scenario.consultaVigente(APRIL)).contains(created);
    }

    @Test
    void extendedMonthsStayOutOfAccumulated() {
        Budget previous = previous();
        scenario.prorrogacao.extend(scenario.condominioId, previous.getId(),
                new BudgetExtensionRequest("2026-04", "assembleia adiada"), "admin");
        SourceFile march = scenario.fluxo("fluxo-2026-03.pdf", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), 1);
        SourceFile april = scenario.fluxo("fluxo-2026-04.pdf", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), 1);
        debit(march, "100.00", LocalDate.of(2026, 3, 10));
        debit(april, "250.00", LocalDate.of(2026, 4, 10));

        PrevistoRealizado r = scenario.previstoRealizado.consultar(scenario.condominioId, "acumulado",
                previous.getId());

        assertThat(r.meses()).hasSize(13);
        assertThat(r.meses().subList(0, 12)).noneMatch(MesExercicio::prorrogado);
        MesExercicio last = r.meses().getLast();
        assertThat(last.mes()).isEqualTo("2026-04");
        assertThat(last.prorrogado()).isTrue();
        assertThat(last.despesaRealizada()).isEqualByComparingTo("250.00");
        assertThat(r.mesesSomados()).containsExactly("2026-03");
        assertThat(r.totais().despesaRealizada()).isEqualByComparingTo("100.00");
        assertThat(r.avisos()).extracting(Aviso::texto).contains("PO prorrogada até 04/2026: abr/2026 aparece depois do"
                + " exercício, marcado \"prorrogado\", e não entra no acumulado.");

        PrevistoRealizado month = scenario.previstoRealizado.consultar(scenario.condominioId, "2026-04", null);
        assertThat(month.totais().despesaRealizada()).isEqualByComparingTo("250.00");
    }

    @Test
    void undoGoesBackToNoBudgetAndGoesToTrail() {
        Budget previous = previous();
        scenario.prorrogacao.extend(scenario.condominioId, previous.getId(),
                new BudgetExtensionRequest("2026-04", "assembleia adiada"), "admin");

        scenario.prorrogacao.undo(scenario.condominioId, previous.getId(), "admin");

        assertThat(scenario.consultaVigente(APRIL)).isEmpty();
        assertThat(scenario.eventos.getLast().getType()).isEqualTo(BudgetEvent.EXTENSION_UNDONE);
        assertThatThrownBy(() -> scenario.prorrogacao.undo(scenario.condominioId, previous.getId(), "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    /** Budget of the test fiscal year 04/2025 to 03/2026 (the pilot's, confirmed with those dates). */
    private Budget previous() {
        Budget budget = scenario.lerPo(PoDoPiloto.padrao());
        var p = scenario.pedidoDoPiloto(budget);
        scenario.confirmacao.confirm(scenario.condominioId, budget.getId(), new BudgetConfirmationRequest("2025-04",
                "2026-03",
                p.minutesFileId(), false, LocalDate.of(2025, 3, 30), p.effectiveCodes(), p.funds(), false, false, null),
                "admin");
        return budget;
    }

    private void debit(SourceFile cashFlow, String amount, LocalDate date) {
        var read = new LedgerEntryData(1, scenario.lancamentos.size() + 1, date, "9999", "Teste", "", "Teste",
                BigDecimal.ZERO.setScale(2), new BigDecimal(amount), BigDecimal.ZERO.setScale(2),
                new Enrichment(null, null, null, false, false));
        scenario.lancamentos.add(new LedgerEntry(scenario.condominioId, cashFlow.getId(), scenario.ordinario.getId(),
                read));
    }
}
