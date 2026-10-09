package br.com.condominioauditoria.api.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.api.model.audit.Finding;
import br.com.condominioauditoria.api.model.audit.FindingEvent;
import br.com.condominioauditoria.api.model.audit.FindingEvidence;
import br.com.condominioauditoria.api.model.audit.RecalculationTrigger;
import br.com.condominioauditoria.api.model.enums.FindingStatus;
import br.com.condominioauditoria.api.model.enums.Severity;
import br.com.condominioauditoria.api.repository.audit.FindingEventRepository;
import br.com.condominioauditoria.api.repository.audit.FindingEvidenceRepository;
import br.com.condominioauditoria.api.repository.audit.FindingRepository;
import br.com.condominioauditoria.api.service.audit.FindingSyncService.AssessedFinding;
import br.com.condominioauditoria.api.service.audit.FindingSyncService.Evidence;
import br.com.condominioauditoria.api.service.audit.FindingSyncService.SyncResult;
import br.com.condominioauditoria.api.service.audit.rule.MonthlyOverrunRule;
import br.com.condominioauditoria.api.service.audit.rule.UnmappedAccountRule;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * RF-03.1.12 and Q27: idempotent recalculation, "no longer applies", reopening of the same finding, human marking kept.
 */
class FindingSyncServiceTest {

    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
    private static final Set<String> RULES = Set.of(UnmappedAccountRule.CODE);

    private final UUID condominium = UUID.randomUUID();
    private final List<Finding> findings = new ArrayList<>();
    private final List<FindingEvidence> evidence = new ArrayList<>();
    private final List<FindingEvent> events = new ArrayList<>();
    private final FindingSyncService sync;

    FindingSyncServiceTest() {
        FindingRepository findingRepo = mock(FindingRepository.class);
        when(findingRepo.save(any())).thenAnswer(i -> {
            if (!findings.contains(i.<Finding>getArgument(0))) {
                findings.add(i.getArgument(0));
            }
            return i.getArgument(0);
        });
        when(findingRepo.findByCondominiumIdAndReferenceMonthAndRuleIn(any(), any(), any())).thenAnswer(i -> findings
                .stream().filter(a -> a.getCondominiumId().equals(i.getArgument(0))
                        && a.getReferenceMonth().atDay(1).equals(i.getArgument(1))
                        && i.<Collection<String>>getArgument(2).contains(a.getRule())).toList());
        FindingEvidenceRepository evidenceRepo = mock(FindingEvidenceRepository.class);
        when(evidenceRepo.save(any())).thenAnswer(i -> {
            evidence.add(i.getArgument(0));
            return i.getArgument(0);
        });
        FindingEventRepository eventRepo = mock(FindingEventRepository.class);
        when(eventRepo.save(any())).thenAnswer(i -> {
            events.add(i.getArgument(0));
            return i.getArgument(0);
        });
        sync = new FindingSyncService(findingRepo, evidenceRepo, eventRepo);
    }

    @Test
    void twoRecalculationsWithTheSameInputLeaveASingleFinding() {
        SyncResult first = sync.synchronize(condominium, SEPTEMBER, RULES, List.of(account8888()),
                trigger("fluxo gravado"));
        SyncResult second = sync.synchronize(condominium, SEPTEMBER, RULES, List.of(account8888()),
                trigger("fluxo gravado de novo"));

        assertThat(first).isEqualTo(new SyncResult(1, 0, 0, 0));
        assertThat(second).isEqualTo(new SyncResult(0, 0, 0, 1));
        assertThat(findings).singleElement().satisfies(a -> {
            assertThat(a.getStatus()).isEqualTo(FindingStatus.ABERTO);
            assertThat(a.getSeverity()).isEqualTo(Severity.ATENCAO);
        });
        assertThat(evidence).hasSize(1);
        assertThat(events).hasSize(1);
    }

    @Test
    void clearedConditionBecomesNoLongerAppliesAndReturnsOnTheSameFinding() {
        sync.synchronize(condominium, SEPTEMBER, RULES, List.of(account8888()), trigger("fluxo gravado"));
        Finding a = findings.getFirst();

        sync.synchronize(condominium, SEPTEMBER, RULES, List.of(), trigger("de-para da conta 8888 confirmado"));
        sync.synchronize(condominium, SEPTEMBER, RULES, List.of(), trigger("outra mudança"));

        assertThat(a.getStatus()).isEqualTo(FindingStatus.NAO_SE_APLICA_MAIS);
        assertThat(a.getStatusReason()).isEqualTo("de-para da conta 8888 confirmado por admin em 04/10/2026");
        assertThat(events).hasSize(2);

        sync.synchronize(condominium, SEPTEMBER, RULES, List.of(account8888()), trigger("de-para desfeito"));

        assertThat(findings).singleElement().isSameAs(a);
        assertThat(a.getStatus()).isEqualTo(FindingStatus.ABERTO);
        assertThat(events).extracting(FindingEvent::getNewStatus).containsExactly(FindingStatus.ABERTO,
                FindingStatus.NAO_SE_APLICA_MAIS, FindingStatus.ABERTO);
        assertThat(evidence).hasSize(1);
    }

    @Test
    void findingMarkedByAPersonKeepsItsStatusAndOnlyGainsHistory() throws Exception {
        sync.synchronize(condominium, SEPTEMBER, RULES, List.of(account8888()), trigger("fluxo gravado"));
        Finding a = findings.getFirst();
        var state = Finding.class.getDeclaredField("status");
        state.setAccessible(true);
        state.set(a, FindingStatus.JUSTIFICADO); // human marking (RF-02.8, the findings screen does not exist yet)

        sync.synchronize(condominium, SEPTEMBER, RULES, List.of(), trigger("de-para da conta 8888 confirmado"));

        assertThat(a.getStatus()).isEqualTo(FindingStatus.JUSTIFICADO);
        assertThat(a.isConditionPresent()).isFalse();
        assertThat(events.getLast().getNewStatus()).isEqualTo(FindingStatus.JUSTIFICADO);
        assertThat(events.getLast().getReason()).contains("estado marcado por pessoa mantido");
    }

    @Test
    void ruleNotAssessedInTheMonthLeavesItsFindingsUnchanged() {
        sync.synchronize(condominium, SEPTEMBER, Set.of(UnmappedAccountRule.CODE, MonthlyOverrunRule.CODE),
                List.of(account8888(), new AssessedFinding(MonthlyOverrunRule.CODE, "1", Severity.CRITICO,
                        "fundo-condominio",
                        "excesso de 21,9% do previsto do mês", List.of())), trigger("fluxo gravado"));

        sync.synchronize(condominium, SEPTEMBER, RULES, List.of(account8888()), trigger("limite descadastrado"));

        assertThat(findings).hasSize(2).allMatch(a -> a.getStatus() == FindingStatus.ABERTO);
    }

    private static AssessedFinding account8888() {
        return new AssessedFinding(UnmappedAccountRule.CODE, UnmappedAccountRule.VERSION, UnmappedAccountRule.SEVERITY,
                UnmappedAccountRule.target("8888"), "Conta 8888 sem linha da PO",
                List.of(new Evidence(UUID.randomUUID(), "a".repeat(64), 3, "Lançamento de 30/09/2026", null)));
    }

    private static RecalculationTrigger trigger(String description) {
        return new RecalculationTrigger(description, "admin", Instant.parse("2026-10-04T15:00:00Z"));
    }
}
