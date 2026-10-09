package br.com.condominioauditoria.api.service.audit;

import br.com.condominioauditoria.api.model.audit.Finding;
import br.com.condominioauditoria.api.model.audit.FindingEvent;
import br.com.condominioauditoria.api.model.audit.FindingEvidence;
import br.com.condominioauditoria.api.model.audit.RecalculationTrigger;
import br.com.condominioauditoria.api.model.enums.FindingStatus;
import br.com.condominioauditoria.api.model.enums.Severity;
import br.com.condominioauditoria.api.repository.audit.FindingEventRepository;
import br.com.condominioauditoria.api.repository.audit.FindingEvidenceRepository;
import br.com.condominioauditoria.api.repository.audit.FindingRepository;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Saves findings by the unique key (condominium, rule, reference month, target): registering again does not duplicate.
 * A finding is never deleted; recalculation only changes the system status (open, no longer applies) and saves the
 * event in the history.
 */
@Service
public class FindingSyncService {

    /** Evidence of a finding; position = place in the list. */
    public record Evidence(UUID fileId, String sha256, Integer page, String reference, UUID budgetLineId) {
    }

    /** Finding assessed by a rule in a recalculation (not saved yet). */
    public record AssessedFinding(String rule, String ruleVersion, Severity severity, String target, String description,
            List<Evidence> evidence) {

        public AssessedFinding {
            Objects.requireNonNull(rule, "regra");
            Objects.requireNonNull(target, "alvo");
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
        }
    }

    /** What a recalculation changed (for logs and tests). */
    public record SyncResult(int opened, int reopened, int closed, int kept) {
    }

    private final FindingRepository findings;
    private final FindingEvidenceRepository evidenceRecords;
    private final FindingEventRepository events;

    public FindingSyncService(FindingRepository findings, FindingEvidenceRepository evidenceRecords,
            FindingEventRepository events) {
        this.findings = findings;
        this.evidenceRecords = evidenceRecords;
        this.events = events;
    }

    public Finding register(UUID condominiumId, String rule, String ruleVersion, Severity severity,
            YearMonth referenceMonth, String target, String description, List<Evidence> evidence) {
        return findings.findByCondominiumIdAndRuleAndReferenceMonthAndTarget(condominiumId, rule,
                referenceMonth.atDay(1), target)
                .orElseGet(() -> create(condominiumId, rule, ruleVersion, severity, referenceMonth, target, description,
                        evidence, "achado aberto pela regra " + rule + " (versão " + ruleVersion + ")", "sistema",
                        Instant.now()));
    }

    /**
     * Applies the result of a month's recalculation for the assessed {@code rules} (RF-03.1.12, Q27):
     * <ul>
     * <li>assessed without a finding: opens a new one, with the evidence;</li>
     * <li>assessed with a finding whose condition had ceased to exist: the <b>same</b> finding returns to "open";</li>
     * <li>finding of the rule not assessed now: the condition no longer exists; if it was open, it becomes "no longer
     * applies", with the reason;</li>
     * <li>finding marked by a person (justified, resolved, false positive) keeps its status; the change of condition
     * only goes into the history.</li>
     * </ul>
     * Running twice with the same input changes nothing the second time: there is only one finding per rule, month
     * and target.
     */
    public SyncResult synchronize(UUID condominiumId, YearMonth referenceMonth, Collection<String> rules,
            List<AssessedFinding> assessed, RecalculationTrigger trigger) {
        Map<String, Finding> existing = new HashMap<>();
        findings.findByCondominiumIdAndReferenceMonthAndRuleIn(condominiumId, referenceMonth.atDay(1),
                List.copyOf(rules))
                .forEach(a -> existing.put(key(a.getRule(), a.getTarget()), a));
        int opened = 0;
        int reopened = 0;
        int closed = 0;
        int kept = 0;
        java.util.Set<String> present = new java.util.HashSet<>();
        for (AssessedFinding item : assessed) {
            if (!rules.contains(item.rule())) {
                throw new IllegalArgumentException("Regra " + item.rule() + " fora das regras recalculadas");
            }
            String k = key(item.rule(), item.target());
            if (!present.add(k)) {
                continue;
            }
            Finding a = existing.get(k);
            if (a == null) {
                create(condominiumId, item.rule(), item.ruleVersion(), item.severity(), referenceMonth, item.target(),
                        item.description(), item.evidence(), trigger.text(), trigger.username(), trigger.occurredAt());
                opened++;
                continue;
            }
            FindingStatus before = a.getStatus();
            if (a.restoreCondition("a condição voltou: " + trigger.text(), trigger.occurredAt())) {
                findings.save(a);
                events.save(new FindingEvent(a, before, "a condição voltou: " + trigger.text(), trigger.username(),
                        trigger.occurredAt()));
                reopened++;
            } else {
                kept++;
            }
        }
        for (var e : existing.entrySet()) {
            if (present.contains(e.getKey())) {
                continue;
            }
            Finding a = e.getValue();
            FindingStatus before = a.getStatus();
            if (a.clearCondition(trigger.text(), trigger.occurredAt())) {
                findings.save(a);
                events.save(new FindingEvent(a, before, a.getStatus().isSystemSet() ? trigger.text()
                        : "a condição deixou de existir (estado marcado por pessoa mantido): " + trigger.text(),
                        trigger.username(), trigger.occurredAt()));
                closed++;
            }
        }
        return new SyncResult(opened, reopened, closed, kept);
    }

    private Finding create(UUID condominiumId, String rule, String ruleVersion, Severity severity,
            YearMonth referenceMonth, String target, String description, List<Evidence> evidence, String reason,
            String username, Instant at) {
        Finding created = findings.save(new Finding(condominiumId, rule, ruleVersion, severity, referenceMonth, target,
                description, at));
        for (int i = 0; i < evidence.size(); i++) {
            Evidence e = evidence.get(i);
            evidenceRecords.save(new FindingEvidence(created.getId(), i + 1, e.fileId(), e.sha256(), e.page(),
                    e.reference(), e.budgetLineId()));
        }
        events.save(new FindingEvent(created, null, reason, username, at));
        return created;
    }

    private static String key(String rule, String target) {
        return rule + "\u0000" + target;
    }
}
