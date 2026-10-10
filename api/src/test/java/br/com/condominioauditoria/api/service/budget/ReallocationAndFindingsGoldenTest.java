package br.com.condominioauditoria.api.service.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import br.com.condominioauditoria.api.dto.request.budget.AccountMappingBatchRequest;
import br.com.condominioauditoria.api.dto.request.budget.MappingTargetRequest;
import br.com.condominioauditoria.api.dto.request.budget.ReallocationRequest;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualLineResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualWarningResponse;
import br.com.condominioauditoria.api.dto.response.budget.EvidenceResponse;
import br.com.condominioauditoria.api.dto.response.budget.ReallocationResponse;
import br.com.condominioauditoria.api.event.BudgetChanged;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.Enrichment;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.LedgerEntryData;
import br.com.condominioauditoria.api.model.accounting.LedgerEntry;
import br.com.condominioauditoria.api.model.audit.Finding;
import br.com.condominioauditoria.api.model.audit.FindingEvent;
import br.com.condominioauditoria.api.model.budget.ReallocationEvent;
import br.com.condominioauditoria.api.model.enums.AccountMappingBatchAction;
import br.com.condominioauditoria.api.model.enums.FindingStatus;
import br.com.condominioauditoria.api.model.enums.MappingTargetType;
import br.com.condominioauditoria.api.model.enums.Severity;
import br.com.condominioauditoria.api.service.audit.rule.MonthlyOverrunRule;
import br.com.condominioauditoria.api.service.audit.rule.UnmappedAccountRule;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/**
 * ADR 0004, step 8, with the September/2026 acceptance case (private golden) going through the services: minimal
 * reallocation (RF-03.1.7), stable entry key (survives reprocessing) and findings recalculation (RF-03.1.6,
 * RF-03.1.11 and RF-03.1.12, Q27). Skipped without data/golden/privado.
 */
class ReallocationAndFindingsGoldenTest {

    private static final String SEPTEMBER = "2026-09";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            .withZone(ZoneId.of("America/Sao_Paulo"));

    @Test
    void reallocatingCardPurchasesTo179AndUndoingRestoresPreviousAmount() {
        SeptemberGolden g = golden();
        g.confirmMap();
        BudgetScenario c = g.scenario;
        BudgetVsActualLineResponse before = line(query(g), "1.7.9");
        List<EvidenceResponse> toReallocate = evidence(g, BudgetVsActualCalculator.TARGET_TO_REALLOCATE);
        assertThat(toReallocate).isNotEmpty().allMatch(ev -> "1064".equals(ev.account()));
        UUID line179 = g.line("1.7.9").getId();

        List<ReallocationResponse> done = toReallocate.stream().map(ev -> c.reallocation.reallocate(c.condominiumId,
                new ReallocationRequest(ev.entryId(), line179), "gestor")).toList();
        BudgetVsActualResponse after = query(g);

        // RF-03.1.7: 1.7.9 at 5.522,25 (+3.222,25); "a realocar" zeroed; actual expense the same
        assertThat(line(after, "1.7.9").actual()).isEqualByComparingTo("5522.25");
        assertThat(line(after, "1.7.9").difference()).isEqualByComparingTo("3222.25");
        assertThat(after.toReallocate().total()).isEqualByComparingTo("0.00");
        assertThat(after.totals().actualExpense()).isEqualByComparingTo("446176.89");
        // The original entry stays in account 1064, with the reallocation mark
        List<EvidenceResponse> of179 = g.scenario.budgetVsActual.evidence(c.condominiumId, SEPTEMBER, null,
                BudgetVsActualCalculator.lineTarget(line179));
        String today = DATE.format(Instant.now());
        assertThat(of179.stream().filter(ev -> "1064".equals(ev.account())).toList()).hasSize(toReallocate.size())
                .allSatisfy(ev -> {
                    assertThat(ev.reallocation()).isEqualTo("realocado para 1.7.9 " + g.line("1.7.9").getDescription()
                            + " por gestor em " + today);
                    assertThat(ev.reallocationId()).isIn(done.stream().map(ReallocationResponse::id).toList());
                    assertThat(ev.memo()).isNotBlank();
                });
        assertThat(c.reallocationEvents).hasSize(toReallocate.size())
                .allMatch(e -> e.getAction().equals(ReallocationEvent.REALLOCATED) && e.getUsername().equals("gestor"));
        // Reallocating the same entry again is refused (one active reallocation per entry)
        assertThatThrownBy(() -> c.reallocation.reallocate(c.condominiumId, new ReallocationRequest(
                toReallocate.getFirst().entryId(), line179), "gestor")).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("já realocado");

        done.forEach(r -> c.reallocation.undo(c.condominiumId, r.id(), "admin"));
        BudgetVsActualResponse undone = query(g);

        assertThat(line(undone, "1.7.9").actual()).isEqualByComparingTo(before.actual());
        assertThat(line(undone, "1.7.9").difference()).isEqualByComparingTo(before.difference());
        assertThat(undone.toReallocate().total()).isEqualByComparingTo("1050.93");
        assertThat(undone.totals().actualExpense()).isEqualByComparingTo("446176.89");
        // Nothing is deleted: the reallocations stay ended, with both events
        assertThat(c.reallocation.list(c.condominiumId, g.budget.getId())).hasSize(done.size())
                .allSatisfy(r -> {
                    assertThat(r.active()).isFalse();
                    assertThat(r.undoneBy()).isEqualTo("admin");
                });
        assertThat(c.reallocationEvents).filteredOn(e -> e.getAction().equals(ReallocationEvent.UNDONE))
                .hasSize(done.size());
        assertThat(c.published).filteredOn(BudgetChanged.class::isInstance).hasSizeGreaterThanOrEqualTo(
                2 * done.size());
    }

    @Test
    void onlyOperatingFundEntryToReallocateIsReallocated() {
        SeptemberGolden g = golden();
        g.confirmMap();
        BudgetScenario c = g.scenario;
        EvidenceResponse concierge = evidence(g,
                BudgetVsActualCalculator.lineTarget(g.line("1.3.10").getId())).getFirst();

        assertThatThrownBy(() -> c.reallocation.reallocate(c.condominiumId, new ReallocationRequest(concierge.entryId(),
                g.line("1.7.9").getId()), "gestor")).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("a realocar");
        EvidenceResponse card = evidence(g, BudgetVsActualCalculator.TARGET_TO_REALLOCATE).getFirst();
        assertThatThrownBy(() -> c.reallocation.reallocate(c.condominiumId, new ReallocationRequest(card.entryId(),
                g.line("1.9.1").getId()), "gestor")).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("linha de despesa");
        assertThat(c.reallocations).isEmpty();
    }

    @Test
    void reallocationSurvivesReprocessingSameCashFlow() {
        SeptemberGolden g = golden();
        g.confirmMap();
        BudgetScenario c = g.scenario;
        UUID line179 = g.line("1.7.9").getId();
        List<EvidenceResponse> toReallocate = evidence(g, BudgetVsActualCalculator.TARGET_TO_REALLOCATE);
        toReallocate.forEach(ev -> c.reallocation.reallocate(c.condominiumId, new ReallocationRequest(ev.entryId(),
                line179), "gestor"));
        Set<UUID> idsBefore = c.ledgerEntries.stream().map(LedgerEntry::getId).collect(Collectors.toSet());

        g.reprocessCashFlow();
        BudgetVsActualResponse r = query(g);

        // The entries have new ids, and the reallocation still holds by the stable key
        assertThat(c.ledgerEntries).noneMatch(l -> idsBefore.contains(l.getId()));
        assertThat(line(r, "1.7.9").actual()).isEqualByComparingTo("5522.25");
        assertThat(r.toReallocate().total()).isEqualByComparingTo("0.00");
        assertThat(r.warnings()).extracting(BudgetVsActualWarningResponse::code).doesNotContain("REALLOCATION_WITHOUT_ENTRY",
                "REALLOCATION_WITHOUT_EFFECT");
        assertThat(evidence(g, BudgetVsActualCalculator.lineTarget(line179)))
                .filteredOn(ev -> "1064".equals(ev.account())).hasSize(toReallocate.size())
                .allMatch(ev -> !idsBefore.contains(ev.entryId()) && ev.reallocation() != null);
    }

    @Test
    void reallocationWithoutMatchingEntryIsNotSilentlySummed() {
        SeptemberGolden g = golden();
        g.confirmMap();
        var orphan = new BudgetVsActualCalculator.ReallocatedEntry(UUID.randomUUID(), "0".repeat(64), g.cashFlowFileId,
                LocalDate.of(2026, 9, 15), "1064", new BigDecimal("99.90"), 7, g.line("1.7.9").getId(), "gestor",
                Instant.EPOCH);

        BudgetVsActualResponse r = BudgetVsActualCalculator.calculate(g.input(new BudgetVsActualCalculator.Month(
                YearMonth.of(2026, 9)), List.of(g.septemberCashFlow()), List.of(orphan))).result();

        assertThat(line(r, "1.7.9").actual()).isEqualByComparingTo(line(g.september().result(), "1.7.9")
                .actual());
        assertThat(r.toReallocate().total()).isEqualByComparingTo("1050.93");
        assertThat(r.warnings()).filteredOn(a -> a.code().equals("REALLOCATION_WITHOUT_ENTRY")).singleElement()
                .satisfies(a -> assertThat(a.text()).contains("15/09/2026", "conta 1064", "R$ 99,90", "página 7"));
    }

    @Test
    void testEntryWithoutMappingRaisesOneAttentionFindingEvenWithTwoRecalculations() {
        SeptemberGolden g = golden();
        g.confirmMap();
        BudgetScenario c = g.scenario;
        LedgerEntry test = testEntry(g, "8888", "CONTA DE TESTE", "500.00");

        c.afterCommit();
        recalculate(c, "primeiro recálculo de teste");
        recalculate(c, "segundo recálculo de teste");

        List<Finding> september = findingsOf(c, YearMonth.of(2026, 9));
        assertThat(september).singleElement().satisfies(a -> {
            assertThat(a.getRule()).isEqualTo(UnmappedAccountRule.CODE);
            assertThat(a.getRuleVersion()).isEqualTo(UnmappedAccountRule.VERSION);
            assertThat(a.getSeverity()).isEqualTo(Severity.WARNING);
            assertThat(a.getTarget()).isEqualTo("account:8888");
            assertThat(a.getStatus()).isEqualTo(FindingStatus.OPEN);
            assertThat(a.getDescription()).contains("Conta 8888 CONTA DE TESTE", "09/2026", "R$ 500,00",
                    "1 lançamento", "sem linha da PO", "verificar o de-para");
        });
        Finding finding = september.getFirst();
        assertThat(c.evidence).filteredOn(e -> e.getFindingId().equals(finding.getId())).singleElement()
                .satisfies(e -> {
                    assertThat(e.getFileId()).isEqualTo(g.cashFlowFileId);
                    assertThat(e.getSha256()).isEqualTo(g.file.getSha256());
                    assertThat(e.getPage()).isEqualTo(test.getPage());
                    assertThat(e.getReference()).contains("30/09/2026", "conta 8888", "R$ 500,00");
                });
        assertThat(events(c, finding)).hasSize(1);
        // No other finding in September: overrun of 8,6%, below 20%
        assertThat(c.findings).noneMatch(a -> a.getRule().equals(MonthlyOverrunRule.CODE));
    }

    @Test
    void findingWhoseConditionEndsBecomesNoLongerAppliesAndReturnsIfConditionReturns() {
        SeptemberGolden g = golden();
        g.confirmMap();
        BudgetScenario c = g.scenario;
        testEntry(g, "8888", "CONTA DE TESTE", "500.00");
        c.afterCommit();
        Finding finding = findingsOf(c, YearMonth.of(2026, 9)).getFirst();

        // Q27: the Admin confirms the account mapping; the finding becomes "não se aplica mais", with the reason
        c.accountMapping.setTarget(c.condominiumId, g.budget.getId(), "8888",
                new MappingTargetRequest(MappingTargetType.BUDGET_LINE,
                g.line("1.7.8").getId(), null, true), "admin");
        c.afterCommit();

        String today = DATE.format(Instant.now());
        assertThat(finding.getStatus()).isEqualTo(FindingStatus.NO_LONGER_APPLIES);
        assertThat(finding.getStatusReason()).isEqualTo("de-para da conta 8888 confirmado por admin em " + today);
        assertThat(finding.isConditionPresent()).isFalse();
        assertThat(c.evidence).filteredOn(e -> e.getFindingId().equals(finding.getId())).hasSize(1);

        // The Admin undoes the account mapping: the condition comes back, and the SAME finding goes back to "aberto"
        c.accountMapping.batch(c.condominiumId, g.budget.getId(),
                new AccountMappingBatchRequest(AccountMappingBatchAction.REJECT,
                List.of("8888")), "admin");
        c.afterCommit();

        assertThat(findingsOf(c, YearMonth.of(2026, 9))).singleElement().isSameAs(finding);
        assertThat(finding.getStatus()).isEqualTo(FindingStatus.OPEN);
        assertThat(events(c, finding)).extracting(FindingEvent::getNewStatus).containsExactly(FindingStatus.OPEN,
                FindingStatus.NO_LONGER_APPLIES, FindingStatus.OPEN);
        assertThat(events(c, finding).get(2).getReason()).isEqualTo("a condição voltou: de-para da conta 8888"
                + " recusado por admin em " + today);
    }

    @Test
    void criticalRule20FindingStopsApplyingWhenMappingRemovesOverrun() {
        SeptemberGolden g = golden();
        g.confirmMap();
        BudgetScenario c = g.scenario;
        // Test month: 60.000,00 more on the front desk (account 1442 → 1.3.10) takes the overrun to 98.880,19 (21,9%)
        testEntry(g, "1442", "VIGIA E PORTARIA", "60000.00");
        c.afterCommit();

        Finding critical = c.findings.stream().filter(a -> a.getRule().equals(MonthlyOverrunRule.CODE)).findFirst()
                .orElseThrow();
        assertThat(critical.getSeverity()).isEqualTo(Severity.CRITICAL);
        assertThat(critical.getReferenceMonth()).isEqualTo(YearMonth.of(2026, 9));
        assertThat(critical.getDescription()).startsWith("excesso de 21,9% do previsto do mês; a Conv. 16.2 exige"
                + " aprovação em AGE para o excedente; verificar ata.").contains("R$ 98.880,19", "R$ 90.324,03");
        assertThat(c.evidence).filteredOn(e -> e.getFindingId().equals(critical.getId()))
                .anyMatch(e -> e.getBudgetLineId() != null && e.getReference().startsWith("PO, linha 1.3.10"))
                .anyMatch(e -> e.getFileId().equals(g.cashFlowFileId));

        // Account 1442 becomes an adjustment in the mapping: the overrun drops below 20% and the finding no longer
        // applies
        c.accountMapping.setTarget(c.condominiumId, g.budget.getId(), "1442",
                new MappingTargetRequest(MappingTargetType.ADJUSTMENT,
                null,
                "teste", true), "admin");
        c.afterCommit();

        assertThat(critical.getStatus()).isEqualTo(FindingStatus.NO_LONGER_APPLIES);
        assertThat(critical.getStatusReason()).startsWith("de-para da conta 1442 confirmado por admin em ");
        assertThat(c.findings).filteredOn(a -> a.getRule().equals(MonthlyOverrunRule.CODE)).hasSize(1);
    }

    @Test
    void monthWithTwoCashFlowsDoesNotChangeFindings() {
        SeptemberGolden g = golden();
        g.confirmMap();
        BudgetScenario c = g.scenario;
        testEntry(g, "8888", "CONTA DE TESTE", "500.00");
        c.afterCommit();
        Finding finding = findingsOf(c, YearMonth.of(2026, 9)).getFirst();

        c.cashFlow("fluxo-corrigido-2026-09.pdf", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 0);
        c.accountMapping.setTarget(c.condominiumId, g.budget.getId(), "8888",
                new MappingTargetRequest(MappingTargetType.BUDGET_LINE,
                g.line("1.7.8").getId(), null, true), "admin");
        c.afterCommit();

        assertThat(finding.getStatus()).isEqualTo(FindingStatus.OPEN);
        assertThat(events(c, finding)).hasSize(1);
    }

    private static LedgerEntry testEntry(SeptemberGolden g, String account, String name, String amount) {
        var read = new LedgerEntryData(99, 9000 + g.scenario.ledgerEntries.size(), LocalDate.of(2026, 9, 30), account,
                name,
                "", "Lançamento de teste", BigDecimal.ZERO.setScale(2), new BigDecimal(amount),
                BigDecimal.ZERO.setScale(2), new Enrichment(null, null, null, false, false));
        LedgerEntry l = new LedgerEntry(g.scenario.condominiumId, g.cashFlowFileId, g.scenario.operatingFund.getId(),
                read);
        g.scenario.ledgerEntries.add(l);
        return l;
    }

    private static void recalculate(BudgetScenario c, String description) {
        c.recalculation.recalculate(BudgetChanged.of(c.condominiumId, description, "sistema", Instant.now()));
    }

    private static List<Finding> findingsOf(BudgetScenario c, YearMonth month) {
        return c.findings.stream().filter(a -> a.getReferenceMonth().equals(month)).toList();
    }

    private static List<FindingEvent> events(BudgetScenario c, Finding a) {
        return c.findingEvents.stream().filter(e -> e.getFindingId().equals(a.getId())).toList();
    }

    private static BudgetVsActualResponse query(SeptemberGolden g) {
        return g.scenario.budgetVsActual.get(g.scenario.condominiumId, SEPTEMBER, null);
    }

    private static List<EvidenceResponse> evidence(SeptemberGolden g, String target) {
        return g.scenario.budgetVsActual.evidence(g.scenario.condominiumId, SEPTEMBER, null, target);
    }

    private static BudgetVsActualLineResponse line(BudgetVsActualResponse r, String code) {
        return r.groups().stream().flatMap(gr -> gr.lines().stream()).filter(l -> l.code().equals(code))
                .findFirst().orElseThrow();
    }

    private static SeptemberGolden golden() {
        Optional<SeptemberGolden> g = SeptemberGolden.load();
        assumeTrue(g.isPresent() && SeptemberGolden.map().isPresent(), "golden privado ausente");
        return g.get();
    }
}
