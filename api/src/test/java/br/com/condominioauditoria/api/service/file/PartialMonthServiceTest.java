package br.com.condominioauditoria.api.service.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.condominioauditoria.api.dto.request.file.PartialMonthRequest;
import br.com.condominioauditoria.api.event.BudgetChanged;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.Enrichment;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.LedgerEntryData;
import br.com.condominioauditoria.api.model.accounting.LedgerEntry;
import br.com.condominioauditoria.api.model.enums.BudgetVsActualStatus;
import br.com.condominioauditoria.api.model.enums.PartialMonthAction;
import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.service.budget.BudgetScenario;
import br.com.condominioauditoria.api.service.audit.rule.UnmappedAccountRule;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** RF-13.10 and ADR 0007, Decision 5 A: partial month by the upload date, manual mark, replacement and findings. */
class PartialMonthServiceTest {

    private final BudgetScenario scenario = new BudgetScenario();

    @Test
    void cashFlowUploadedBeforeTheEndOfItsPeriodIsPartial() {
        // Uploaded now, period ending in the future: partial until the upload date
        SourceFile open = scenario.cashFlow("fluxo-futuro.pdf", LocalDate.of(2099, 1, 1), LocalDate.of(2099, 1, 31), 1);
        // Period ended before the upload: complete
        SourceFile closed = scenario.cashFlow("fluxo-2026-09.pdf", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                1);

        assertThat(open.isPartial()).isTrue();
        assertThat(open.effectivePartialUntil()).isEqualTo(open.uploadDate());
        assertThat(closed.isPartial()).isFalse();
        assertThat(closed.effectivePartialUntil()).isNull();
    }

    @Test
    void managerMarksAndUnmarksWithTrailAndRecalculation() {
        SourceFile sep = scenario.cashFlow("fluxo-2026-09.pdf", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 1);

        var r = scenario.partialMonths.mark(scenario.condominiumId, sep.getId(), new PartialMonthRequest(true,
                LocalDate.of(2026, 9, 20), "extrato emitido no dia 20"), "gestor");

        assertThat(r.partial()).isTrue();
        assertThat(r.automatic()).isFalse();
        assertThat(r.partialUntil()).isEqualTo(LocalDate.of(2026, 9, 20));
        assertThat(r.changedBy()).isEqualTo("gestor");
        assertThat(scenario.partialMonthEvents).singleElement().satisfies(e -> {
            assertThat(e.getAction()).isEqualTo(PartialMonthAction.MARKED_PARTIAL);
            assertThat(e.isPreviousPartial()).isFalse();
            assertThat(e.isNewPartial()).isTrue();
            assertThat(e.getReason()).isEqualTo("extrato emitido no dia 20");
        });
        assertThat(scenario.published).anySatisfy(p -> assertThat(p).isInstanceOf(BudgetChanged.class));

        var back = scenario.partialMonths.mark(scenario.condominiumId, sep.getId(), new PartialMonthRequest(null, null,
                null), "admin");
        assertThat(back.partial()).isFalse();
        assertThat(back.automatic()).isTrue();
        assertThat(scenario.partialMonthEvents).hasSize(2).last()
                .satisfies(e -> assertThat(e.getAction()).isEqualTo(PartialMonthAction.AUTOMATIC));
        assertThat(scenario.partialMonths.events(scenario.condominiumId, sep.getId())).hasSize(2);
    }

    @Test
    void dateOutsideThePeriodOrAFileThatIsNotACashFlowIsRejected() {
        SourceFile sep = scenario.cashFlow("fluxo-2026-09.pdf", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 1);

        assertThatThrownBy(() -> scenario.partialMonths.mark(scenario.condominiumId, sep.getId(),
                new PartialMonthRequest(true, LocalDate.of(2026, 10, 2), null), "gestor"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));
        assertThatThrownBy(() -> scenario.partialMonths.mark(scenario.condominiumId, scenario.minutes.getId(),
                new PartialMonthRequest(true, null, null), "gestor"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));
    }

    @Test
    void completeCashFlowReplacesThePartialOneWithoutTwoCashFlows() {
        scenario.confirmedBudget();
        SourceFile partial = scenario.cashFlow("fluxo-2026-09-parcial.pdf", LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30), 1);
        partial.markPartial(true, LocalDate.of(2026, 9, 20), null, "gestor", java.time.Instant.now());
        SourceFile complete = scenario.cashFlow("fluxo-2026-09.pdf", LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30), 1);

        scenario.partialMonths.recordReplacements(scenario.condominiumId);
        scenario.partialMonths.recordReplacements(scenario.condominiumId);

        assertThat(scenario.partialMonthEvents).singleElement().satisfies(e -> {
            assertThat(e.getAction()).isEqualTo(PartialMonthAction.SUPERSEDED);
            assertThat(e.getFileId()).isEqualTo(partial.getId());
            assertThat(e.getSupersededById()).isEqualTo(complete.getId());
            assertThat(e.getUsername()).isEqualTo("sistema");
        });
        var month = scenario.budgetVsActual.get(scenario.condominiumId, "2026-09", null);
        assertThat(month.status()).isEqualTo(BudgetVsActualStatus.CALCULATED);
        assertThat(month.months().getFirst().cashFlows()).singleElement()
                .satisfies(f -> assertThat(f.fileId()).isEqualTo(complete.getId()));
        assertThat(scenario.partialMonths.list(scenario.condominiumId)).filteredOn(p -> p.fileId()
                .equals(partial.getId())).singleElement()
                .satisfies(p -> assertThat(p.supersededById()).isEqualTo(complete.getId()));
    }

    @Test
    void partialMonthCreatesNoFinding() {
        scenario.confirmedBudget();
        SourceFile sep = scenario.cashFlow("fluxo-2026-09.pdf", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 1);
        // A debit of an account without mapping: in a complete month it is a "sem linha da PO" finding
        scenario.ledgerEntries.add(new LedgerEntry(scenario.condominiumId, sep.getId(),
                scenario.operatingFund.getId(), new LedgerEntryData(1, 1, LocalDate.of(2026, 9, 5), "1999",
                "CONTA SEM DE-PARA", "", "Teste", BigDecimal.ZERO.setScale(2), new BigDecimal("100.00"),
                BigDecimal.ZERO.setScale(2), new Enrichment(null, null, null, false, false))));
        sep.markPartial(true, LocalDate.of(2026, 9, 20), null, "gestor", java.time.Instant.now());
        scenario.published.add(BudgetChanged.of(scenario.condominiumId, "teste", "gestor", java.time.Instant.now()));

        scenario.afterCommit();
        assertThat(scenario.findings).noneMatch(f -> f.getRule().equals(UnmappedAccountRule.CODE));

        sep.markPartial(false, null, null, "gestor", java.time.Instant.now());
        scenario.published.add(BudgetChanged.of(scenario.condominiumId, "teste", "gestor", java.time.Instant.now()));
        scenario.afterCommit();
        assertThat(scenario.findings).anyMatch(f -> f.getRule().equals(UnmappedAccountRule.CODE));
    }
}
