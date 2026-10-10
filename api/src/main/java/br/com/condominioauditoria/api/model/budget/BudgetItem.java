package br.com.condominioauditoria.api.model.budget;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;

/**
 * Budget item of the condominium's catalog (ADR 0005, Decision 1): lines of different fiscal years linked to the same
 * item correspond. Never deleted; the Admin may rename it.
 */
@Entity
public class BudgetItem {

    @Id
    private UUID id;
    private UUID condominiumId;
    private String name;
    private String groupCode;
    private UUID sourceLineId;
    private String createdBy;
    private Instant createdAt;

    protected BudgetItem() {
    }

    public BudgetItem(UUID condominiumId, String name, String groupCode, UUID sourceLineId, String username,
            Instant at) {
        this.id = UUID.randomUUID();
        this.condominiumId = condominiumId;
        this.name = name;
        this.groupCode = groupCode;
        this.sourceLineId = sourceLineId;
        this.createdBy = username;
        this.createdAt = at;
    }

    public void rename(String name) {
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCondominiumId() {
        return condominiumId;
    }

    public String getName() {
        return name;
    }

    public String getGroupCode() {
        return groupCode;
    }

    public UUID getSourceLineId() {
        return sourceLineId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
