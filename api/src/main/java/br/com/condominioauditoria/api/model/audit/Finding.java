package br.com.condominioauditoria.api.model.audit;

import br.com.condominioauditoria.api.model.enums.FindingStatus;
import br.com.condominioauditoria.api.model.enums.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Indication raised by a rule, with the rule version and the evidence (file, page and hash). Unique per condominium,
 * rule, reference month and target: recalculating never duplicates. The text describes the fact and what to check,
 * never a cause.
 */
@Entity
@Table(name = "achado")
public class Finding {

    @Id
    private UUID id;
    @Column(name = "condominio_id")
    private UUID condominiumId;
    @Column(name = "regra")
    private String rule;
    @Column(name = "versao_regra")
    private String ruleVersion;
    @Column(name = "severidade")
    @Enumerated(EnumType.STRING)
    private Severity severity;
    @Column(name = "competencia")
    private LocalDate referenceMonth;
    @Column(name = "alvo")
    private String target;
    @Column(name = "descricao")
    private String description;
    @Column(name = "estado")
    @Enumerated(EnumType.STRING)
    private FindingStatus status;
    @Column(name = "criado_em")
    private Instant createdAt;
    /** The rule's condition existed at the last recalculation. */
    @Column(name = "condicao_presente")
    private boolean conditionPresent;
    @Column(name = "estado_motivo")
    private String statusReason;
    @Column(name = "estado_em")
    private Instant statusChangedAt;

    protected Finding() {
    }

    public Finding(UUID condominiumId, String rule, String ruleVersion, Severity severity, YearMonth referenceMonth,
            String target, String description, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.condominiumId = condominiumId;
        this.rule = rule;
        this.ruleVersion = ruleVersion;
        this.severity = severity;
        this.referenceMonth = referenceMonth.atDay(1);
        this.target = target;
        this.description = description;
        this.status = FindingStatus.ABERTO;
        this.createdAt = createdAt;
        this.conditionPresent = true;
        this.statusChangedAt = createdAt;
    }

    /**
     * The condition no longer exists. An open finding becomes "no longer applies" (Q27); a finding marked by a person
     * keeps its status. Returns false if nothing changed (the condition was already absent).
     */
    public boolean clearCondition(String reason, Instant at) {
        if (!conditionPresent) {
            return false;
        }
        conditionPresent = false;
        if (status == FindingStatus.ABERTO) {
            status = FindingStatus.NAO_SE_APLICA_MAIS;
            statusReason = reason;
            statusChangedAt = at;
        }
        return true;
    }

    /**
     * The condition is back: the same finding returns to "open" (no new one is created); one marked by a person keeps
     * its status. Returns false if nothing changed (the condition was already present).
     */
    public boolean restoreCondition(String reason, Instant at) {
        if (conditionPresent) {
            return false;
        }
        conditionPresent = true;
        if (status == FindingStatus.NAO_SE_APLICA_MAIS) {
            status = FindingStatus.ABERTO;
            statusReason = reason;
            statusChangedAt = at;
        }
        return true;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCondominiumId() {
        return condominiumId;
    }

    public String getRule() {
        return rule;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public Severity getSeverity() {
        return severity;
    }

    public YearMonth getReferenceMonth() {
        return YearMonth.from(referenceMonth);
    }

    public String getTarget() {
        return target;
    }

    public String getDescription() {
        return description;
    }

    public FindingStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isConditionPresent() {
        return conditionPresent;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public Instant getStatusChangedAt() {
        return statusChangedAt;
    }
}
