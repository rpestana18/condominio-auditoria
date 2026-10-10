package br.com.condominioauditoria.api.model.budget;

import br.com.condominioauditoria.api.model.enums.AccountMappingAction;
import br.com.condominioauditoria.api.model.enums.AccountMappingSource;
import br.com.condominioauditoria.api.model.enums.AccountMappingStatus;
import br.com.condominioauditoria.api.model.enums.MappingTargetType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;

/**
 * Account mapping trail (RF-03.1.4): who, when, account, previous and new target and status. Insert-only: the database
 * rejects update and delete with a trigger (V8). Moves to the general trail once RF-07.4 exists.
 */
@Entity
public class AccountMappingEvent {

    @Id
    private UUID id;
    private UUID condominiumId;
    private UUID budgetId;
    private String accountCode;
    private String accountName;
    @Enumerated(EnumType.STRING)
    private AccountMappingAction action;
    private String username;
    private Instant occurredAt;
    @Enumerated(EnumType.STRING)
    private MappingTargetType previousTargetType;
    private UUID previousBudgetLineId;
    private String previousTarget;
    @Enumerated(EnumType.STRING)
    private AccountMappingStatus previousStatus;
    @Enumerated(EnumType.STRING)
    private MappingTargetType newTargetType;
    private UUID newBudgetLineId;
    private String newTarget;
    @Enumerated(EnumType.STRING)
    private AccountMappingStatus newStatus;
    @Enumerated(EnumType.STRING)
    private AccountMappingSource source;
    private String reason;

    protected AccountMappingEvent() {
    }

    /**
     * @param previous target before the change (null when the account had no mapping yet)
     *
     * @param previousStatus status before the change (null when the account had no mapping yet)
     */
    public AccountMappingEvent(AccountMapping mapping, AccountMappingAction action, MappingTarget previous,
            AccountMappingStatus previousStatus, MappingTarget current,
            String username, Instant at) {
        this.id = UUID.randomUUID();
        this.condominiumId = mapping.getCondominiumId();
        this.budgetId = mapping.getBudgetId();
        this.accountCode = mapping.getAccountCode();
        this.accountName = mapping.getAccountName();
        this.action = action;
        this.username = username;
        this.occurredAt = at;
        if (previous != null) {
            this.previousTargetType = previous.type();
            this.previousBudgetLineId = previous.budgetLineId();
            this.previousTarget = previous.text();
        }
        this.previousStatus = previousStatus;
        this.newTargetType = current.type();
        this.newBudgetLineId = current.budgetLineId();
        this.newTarget = current.text();
        this.newStatus = mapping.getStatus();
        this.source = mapping.getSource();
        this.reason = mapping.getReason();
    }

    public UUID getId() {
        return id;
    }

    public UUID getBudgetId() {
        return budgetId;
    }

    public String getAccountCode() {
        return accountCode;
    }

    public String getAccountName() {
        return accountName;
    }

    public AccountMappingAction getAction() {
        return action;
    }

    public String getUsername() {
        return username;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public MappingTargetType getPreviousTargetType() {
        return previousTargetType;
    }

    public UUID getPreviousBudgetLineId() {
        return previousBudgetLineId;
    }

    public String getPreviousTarget() {
        return previousTarget;
    }

    public AccountMappingStatus getPreviousStatus() {
        return previousStatus;
    }

    public MappingTargetType getNewTargetType() {
        return newTargetType;
    }

    public UUID getNewBudgetLineId() {
        return newBudgetLineId;
    }

    public String getNewTarget() {
        return newTarget;
    }

    public AccountMappingStatus getNewStatus() {
        return newStatus;
    }

    public AccountMappingSource getSource() {
        return source;
    }

    public String getReason() {
        return reason;
    }
}
