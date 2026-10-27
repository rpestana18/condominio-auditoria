package br.com.condominioauditoria.api.model.budget;

import br.com.condominioauditoria.api.model.enums.BudgetLineReviewAction;
import br.com.condominioauditoria.api.model.enums.BudgetLineReviewStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;

/**
 * Budget line review trail (RF-13.12; ADR 0009, Decision 1): who, when, line, previous and new status and the Admin's
 * marks after the change. Insert-only: the database rejects update and delete with a trigger (V21).
 */
@Entity
public class BudgetLineReviewEvent {

    @Id
    private UUID id;
    private UUID condominiumId;
    private UUID budgetId;
    private UUID budgetLineId;
    private String lineCode;
    private String lineDescription;
    @Enumerated(EnumType.STRING)
    private BudgetLineReviewAction action;
    private String username;
    private Instant occurredAt;
    @Enumerated(EnumType.STRING)
    private BudgetLineReviewStatus previousStatus;
    @Enumerated(EnumType.STRING)
    private BudgetLineReviewStatus newStatus;
    private boolean groupAccount;
    private boolean descriptionPrevails;
    private String reason;

    protected BudgetLineReviewEvent() {
    }

    /** Event of a line, after the line changed (previous status given by the caller). */
    public BudgetLineReviewEvent(BudgetLine line, BudgetLineReviewAction action,
            BudgetLineReviewStatus previousStatus, String username, Instant at, String reason) {
        this.id = UUID.randomUUID();
        this.condominiumId = line.getCondominiumId();
        this.budgetId = line.getBudgetId();
        this.budgetLineId = line.getId();
        this.lineCode = line.getEffectiveCode();
        this.lineDescription = line.getDescription();
        this.action = action;
        this.username = username;
        this.occurredAt = at;
        this.previousStatus = previousStatus;
        this.newStatus = line.getReviewStatus();
        this.groupAccount = line.isGroupAccount();
        this.descriptionPrevails = line.isDescriptionPrevails();
        this.reason = reason;
    }

    public UUID getId() {
        return id;
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

    public BudgetLineReviewAction getAction() {
        return action;
    }

    public String getUsername() {
        return username;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public BudgetLineReviewStatus getPreviousStatus() {
        return previousStatus;
    }

    public BudgetLineReviewStatus getNewStatus() {
        return newStatus;
    }

    public boolean isGroupAccount() {
        return groupAccount;
    }

    public boolean isDescriptionPrevails() {
        return descriptionPrevails;
    }

    public String getReason() {
        return reason;
    }
}
