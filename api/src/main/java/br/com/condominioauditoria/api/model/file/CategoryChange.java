package br.com.condominioauditoria.api.model.file;

import br.com.condominioauditoria.api.model.enums.FileCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Record of a category change (RF-01.7): who, when, the previous and the new category. */
@Entity
@Table(name = "arquivo_categoria_historico")
public class CategoryChange {

    @Id
    private UUID id;
    @Column(name = "arquivo_id")
    private UUID fileId;
    @Column(name = "categoria_anterior")
    @Enumerated(EnumType.STRING)
    private FileCategory previousCategory;
    @Column(name = "categoria_nova")
    @Enumerated(EnumType.STRING)
    private FileCategory newCategory;
    @Column(name = "alterado_por")
    private String changedBy;
    @Column(name = "alterado_em")
    private Instant changedAt;

    protected CategoryChange() {
    }

    public CategoryChange(UUID fileId, FileCategory previousCategory, FileCategory newCategory, String changedBy) {
        this.id = UUID.randomUUID();
        this.fileId = fileId;
        this.previousCategory = previousCategory;
        this.newCategory = newCategory;
        this.changedBy = changedBy;
        this.changedAt = Instant.now();
    }

    public UUID getFileId() {
        return fileId;
    }

    public FileCategory getPreviousCategory() {
        return previousCategory;
    }

    public FileCategory getNewCategory() {
        return newCategory;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
