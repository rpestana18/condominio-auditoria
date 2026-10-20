package br.com.condominioauditoria.api.model.file;

import br.com.condominioauditoria.api.model.enums.PartialMonthAction;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Partial month trail (RF-13.10): mark, unmark, back to the automatic rule and replacement by the complete cash flow of
 * the same month. Insert-only: the database rejects update and delete with a trigger (V19).
 */
@Entity
public class PartialMonthEvent {

    @Id
    private UUID id;
    private UUID condominiumId;
    private UUID fileId;
    private UUID supersededById;
    @Enumerated(EnumType.STRING)
    private PartialMonthAction action;
    private boolean previousPartial;
    private boolean newPartial;
    private LocalDate partialUntil;
    private String username;
    private Instant occurredAt;
    private String reason;

    protected PartialMonthEvent() {
    }

    public PartialMonthEvent(SourceFile file, PartialMonthAction action, boolean previousPartial,
            SourceFile supersededBy, String username, Instant at, String reason) {
        this.id = UUID.randomUUID();
        this.condominiumId = file.getCondominiumId();
        this.fileId = file.getId();
        this.supersededById = supersededBy == null ? null : supersededBy.getId();
        this.action = action;
        this.previousPartial = previousPartial;
        this.newPartial = action != PartialMonthAction.SUPERSEDED && file.isPartial();
        this.partialUntil = file.effectivePartialUntil();
        this.username = username;
        this.occurredAt = at;
        this.reason = reason;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCondominiumId() {
        return condominiumId;
    }

    public UUID getFileId() {
        return fileId;
    }

    public UUID getSupersededById() {
        return supersededById;
    }

    public PartialMonthAction getAction() {
        return action;
    }

    public boolean isPreviousPartial() {
        return previousPartial;
    }

    public boolean isNewPartial() {
        return newPartial;
    }

    public LocalDate getPartialUntil() {
        return partialUntil;
    }

    public String getUsername() {
        return username;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getReason() {
        return reason;
    }
}
