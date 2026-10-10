package br.com.condominioauditoria.api.model.audit;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;

/** Where the indication is: file, hash, page and the excerpt (e.g. the budget line). */
@Entity
public class FindingEvidence {

    @Id
    private UUID id;
    private UUID findingId;
    private int position;
    private UUID fileId;
    private String sha256;
    private Integer page;
    private String reference;
    private UUID budgetLineId;

    protected FindingEvidence() {
    }

    public FindingEvidence(UUID findingId, int position, UUID fileId, String sha256, Integer page, String reference,
            UUID budgetLineId) {
        this.id = UUID.randomUUID();
        this.findingId = findingId;
        this.position = position;
        this.fileId = fileId;
        this.sha256 = sha256;
        this.page = page;
        this.reference = reference;
        this.budgetLineId = budgetLineId;
    }

    public UUID getFindingId() {
        return findingId;
    }

    public int getPosition() {
        return position;
    }

    public UUID getFileId() {
        return fileId;
    }

    public String getSha256() {
        return sha256;
    }

    public Integer getPage() {
        return page;
    }

    public String getReference() {
        return reference;
    }

    public UUID getBudgetLineId() {
        return budgetLineId;
    }
}
