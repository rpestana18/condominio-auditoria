package br.com.condominioauditoria.api.model.budget;

import br.com.condominioauditoria.api.model.enums.BudgetItemSource;
import br.com.condominioauditoria.api.model.enums.BudgetItemStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Budget item of a line of a confirmed budget (RF-11.7): exactly one per line. Every change writes a {@link
 * BudgetItemEvent} to the trail, which is insert-only.
 */
@Entity
@Table(name = "linha_rubrica")
public class BudgetLineItem {

    @Id
    private UUID id;
    @Column(name = "condominio_id")
    private UUID condominiumId;
    @Column(name = "previsao_id")
    private UUID budgetId;
    @Column(name = "linha_po_id")
    private UUID budgetLineId;
    @Column(name = "rubrica_id")
    private UUID budgetItemId;
    @Column(name = "estado")
    @Enumerated(EnumType.STRING)
    private BudgetItemStatus status;
    @Column(name = "origem")
    @Enumerated(EnumType.STRING)
    private BudgetItemSource source;
    @Column(name = "motivo")
    private String reason;
    @Column(name = "atualizado_por")
    private String updatedBy;
    @Column(name = "atualizado_em")
    private Instant updatedAt;

    protected BudgetLineItem() {
    }

    public BudgetLineItem(BudgetLine line, BudgetItem item, BudgetItemStatus status, BudgetItemSource source,
            String reason,
            String username, Instant at) {
        this.id = UUID.randomUUID();
        this.condominiumId = line.getCondominiumId();
        this.budgetId = line.getBudgetId();
        this.budgetLineId = line.getId();
        change(item, status, source, reason, username, at);
    }

    public void change(BudgetItem item, BudgetItemStatus status, BudgetItemSource source, String reason,
            String username,
            Instant at) {
        this.budgetItemId = item.getId();
        this.status = status;
        this.source = source;
        this.reason = reason;
        this.updatedBy = username;
        this.updatedAt = at;
    }

    /** Only the status changes (confirm or reject); item, source and reason stay. */
    public void changeStatus(BudgetItemStatus newStatus, String username, Instant at) {
        this.status = newStatus;
        this.updatedBy = username;
        this.updatedAt = at;
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

    public UUID getBudgetItemId() {
        return budgetItemId;
    }

    public BudgetItemStatus getStatus() {
        return status;
    }

    public BudgetItemSource getSource() {
        return source;
    }

    public String getReason() {
        return reason;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
