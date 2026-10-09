package br.com.condominioauditoria.api.model.audit;

import br.com.condominioauditoria.api.model.enums.FindingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Finding history (insert only, the database rejects update and delete): status, condition, reason, who and when. */
@Entity
@Table(name = "evento_achado")
public class FindingEvent {

    @Id
    private UUID id;
    @Column(name = "achado_id")
    private UUID findingId;
    @Column(name = "condominio_id")
    private UUID condominiumId;
    @Column(name = "estado_anterior")
    @Enumerated(EnumType.STRING)
    private FindingStatus previousStatus;
    @Column(name = "estado_novo")
    @Enumerated(EnumType.STRING)
    private FindingStatus newStatus;
    @Column(name = "condicao_presente")
    private boolean conditionPresent;
    @Column(name = "motivo")
    private String reason;
    @Column(name = "usuario")
    private String username;
    @Column(name = "em")
    private Instant occurredAt;

    protected FindingEvent() {
    }

    public FindingEvent(Finding finding, FindingStatus previousStatus, String reason, String username, Instant at) {
        this.id = UUID.randomUUID();
        this.findingId = finding.getId();
        this.condominiumId = finding.getCondominiumId();
        this.previousStatus = previousStatus;
        this.newStatus = finding.getStatus();
        this.conditionPresent = finding.isConditionPresent();
        this.reason = reason;
        this.username = username;
        this.occurredAt = at;
    }

    public UUID getId() {
        return id;
    }

    public UUID getFindingId() {
        return findingId;
    }

    public FindingStatus getPreviousStatus() {
        return previousStatus;
    }

    public FindingStatus getNewStatus() {
        return newStatus;
    }

    public boolean isConditionPresent() {
        return conditionPresent;
    }

    public String getReason() {
        return reason;
    }

    public String getUsername() {
        return username;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
