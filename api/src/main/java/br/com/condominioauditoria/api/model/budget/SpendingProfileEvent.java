package br.com.condominioauditoria.api.model.budget;

import br.com.condominioauditoria.api.model.enums.SpendingProfile;
import br.com.condominioauditoria.api.model.enums.SpendingProfileStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;

/**
 * Spending profile trail (RF-13.3): who, when, line, previous and new profile and status. Insert-only: the database
 * rejects update and delete with a trigger (V19), as in the account mapping trail.
 */
@Entity
public class SpendingProfileEvent {

    @Id
    private UUID id;
    private UUID condominiumId;
    private UUID budgetId;
    private UUID budgetLineId;
    private String lineCode;
    private String lineDescription;
    /** SUGGESTED or CONFIRMED: the same values as the status reached. */
    @Enumerated(EnumType.STRING)
    private SpendingProfileStatus action;
    private String username;
    private Instant occurredAt;
    @Enumerated(EnumType.STRING)
    private SpendingProfile previousProfile;
    @Enumerated(EnumType.STRING)
    private SpendingProfileStatus previousStatus;
    @Enumerated(EnumType.STRING)
    private SpendingProfile newProfile;
    @Enumerated(EnumType.STRING)
    private SpendingProfileStatus newStatus;
    private String reason;

    protected SpendingProfileEvent() {
    }

    /** Event of a line, after the line changed (previous values given by the caller). */
    public SpendingProfileEvent(BudgetLine line, SpendingProfile previousProfile, SpendingProfileStatus previousStatus,
            String username, Instant at) {
        this.id = UUID.randomUUID();
        this.condominiumId = line.getCondominiumId();
        this.budgetId = line.getBudgetId();
        this.budgetLineId = line.getId();
        this.lineCode = line.getEffectiveCode();
        this.lineDescription = line.getDescription();
        this.action = line.getSpendingProfileStatus();
        this.username = username;
        this.occurredAt = at;
        this.previousProfile = previousProfile;
        this.previousStatus = previousStatus;
        this.newProfile = line.getSpendingProfile();
        this.newStatus = line.getSpendingProfileStatus();
        this.reason = line.getSpendingProfileReason();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCondominiumId() {
        return condominiumId;
    }

    public UUID getBudgetId() {
        return budgetId;
    }

    public UUID getBudgetLineId() {
        return budgetLineId;
    }

    public String getLineCode() {
        return lineCode;
    }

    public String getLineDescription() {
        return lineDescription;
    }

    public SpendingProfileStatus getAction() {
        return action;
    }

    public String getUsername() {
        return username;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public SpendingProfile getPreviousProfile() {
        return previousProfile;
    }

    public SpendingProfileStatus getPreviousStatus() {
        return previousStatus;
    }

    public SpendingProfile getNewProfile() {
        return newProfile;
    }

    public SpendingProfileStatus getNewStatus() {
        return newStatus;
    }

    public String getReason() {
        return reason;
    }
}
