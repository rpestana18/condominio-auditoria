package br.com.condominioauditoria.api.model.budget;

import br.com.condominioauditoria.api.model.enums.BudgetItemAction;
import br.com.condominioauditoria.api.model.enums.BudgetItemSource;
import br.com.condominioauditoria.api.model.enums.BudgetItemStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;

/**
 * Budget item trail (RF-11.7): who, when, line, previous and new item and status. Insert-only: the database rejects
 * update and delete with a trigger (V14). Creating and renaming an item have no line.
 */
@Entity
public class BudgetItemEvent {

    @Id
    private UUID id;
    private UUID condominiumId;
    private UUID budgetId;
    private UUID budgetLineId;
    private String lineCode;
    private String lineDescription;
    @Enumerated(EnumType.STRING)
    private BudgetItemAction action;
    private String username;
    private Instant occurredAt;
    private UUID previousItemId;
    private String previousItem;
    @Enumerated(EnumType.STRING)
    private BudgetItemStatus previousStatus;
    private UUID newItemId;
    private String newItem;
    @Enumerated(EnumType.STRING)
    private BudgetItemStatus newStatus;
    @Enumerated(EnumType.STRING)
    private BudgetItemSource source;
    private String reason;

    protected BudgetItemEvent() {
    }

    /**
     * Event of a line.
     *
     * @param previous item before the change (null when the line had no item yet)
     *
     * @param previousStatus status before the change (null when the line had no item yet)
     */
    public static BudgetItemEvent forLine(BudgetLine line, BudgetLineItem current, BudgetItemAction action,
            BudgetItem previous,
            BudgetItemStatus previousStatus, BudgetItem newItem, String username, Instant at) {
        BudgetItemEvent e = new BudgetItemEvent(line.getCondominiumId(), action, newItem, username, at);
        e.budgetId = line.getBudgetId();
        e.budgetLineId = line.getId();
        e.lineCode = line.getEffectiveCode();
        e.lineDescription = line.getDescription();
        if (previous != null) {
            e.previousItemId = previous.getId();
            e.previousItem = previous.getName();
        }
        e.previousStatus = previousStatus;
        e.newStatus = current.getStatus();
        e.source = current.getSource();
        e.reason = current.getReason();
        return e;
    }

    /** Item created (without a line, or from a budget line). */
    public static BudgetItemEvent created(BudgetItem item, BudgetLine source, String username, Instant at) {
        BudgetItemEvent e = new BudgetItemEvent(item.getCondominiumId(), BudgetItemAction.CREATED, item, username, at);
        if (source != null) {
            e.budgetId = source.getBudgetId();
            e.budgetLineId = source.getId();
            e.lineCode = source.getEffectiveCode();
            e.lineDescription = source.getDescription();
        }
        return e;
    }

    public static BudgetItemEvent renamed(BudgetItem item, String previousName, String username, Instant at) {
        BudgetItemEvent e = new BudgetItemEvent(item.getCondominiumId(), BudgetItemAction.RENAMED, item, username,
                at);
        e.previousItemId = item.getId();
        e.previousItem = previousName;
        return e;
    }

    private BudgetItemEvent(UUID condominiumId, BudgetItemAction action, BudgetItem item, String username, Instant at) {
        this.id = UUID.randomUUID();
        this.condominiumId = condominiumId;
        this.action = action;
        this.username = username;
        this.occurredAt = at;
        this.newItemId = item.getId();
        this.newItem = item.getName();
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

    public BudgetItemAction getAction() {
        return action;
    }

    public String getUsername() {
        return username;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public UUID getPreviousItemId() {
        return previousItemId;
    }

    public String getPreviousItem() {
        return previousItem;
    }

    public BudgetItemStatus getPreviousStatus() {
        return previousStatus;
    }

    public UUID getNewItemId() {
        return newItemId;
    }

    public String getNewItem() {
        return newItem;
    }

    public BudgetItemStatus getNewStatus() {
        return newStatus;
    }

    public BudgetItemSource getSource() {
        return source;
    }

    public String getReason() {
        return reason;
    }
}
