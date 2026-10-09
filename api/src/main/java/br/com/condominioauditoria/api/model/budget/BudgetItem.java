package br.com.condominioauditoria.api.model.budget;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Budget item of the condominium's catalog (ADR 0005, Decision 1): lines of different fiscal years linked to the same
 * item correspond. Never deleted; the Admin may rename it.
 */
@Entity
@Table(name = "rubrica")
public class BudgetItem {

    @Id
    private UUID id;
    @Column(name = "condominio_id")
    private UUID condominiumId;
    @Column(name = "nome")
    private String name;
    @Column(name = "grupo_codigo")
    private String groupCode;
    @Column(name = "linha_origem_id")
    private UUID sourceLineId;
    @Column(name = "criada_por")
    private String createdBy;
    @Column(name = "criada_em")
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
