package br.com.condominioauditoria.api.model.budget;

import br.com.condominioauditoria.api.model.accounting.LedgerEntry;
import br.com.condominioauditoria.api.model.accounting.LedgerEntryFingerprint;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Reallocation of an "a realocar" entry to a budget line (RF-03.1.7; RF-02B.4). It is a system layer: the management
 * company's original entry does not change. Keeps the entry's fingerprint and its key, because the entry id changes on
 * each reprocessing. Undoing ends the reallocation; nothing is deleted.
 */
@Entity
public class Reallocation {

    @Id
    private UUID id;
    private UUID condominiumId;
    private UUID budgetId;
    private String entryKey;
    private UUID fileId;
    private String sha256;
    private int page;
    private int position;
    private LocalDate date;
    private String accountCode;
    private String accountName;
    private String document;
    private String memo;
    private BigDecimal amount;
    private UUID budgetLineId;
    private String reallocatedBy;
    private Instant reallocatedAt;
    private String undoneBy;
    private Instant undoneAt;

    protected Reallocation() {
    }

    public Reallocation(Budget budget, LedgerEntry l, String sha256, BudgetLine target, String username,
            Instant at) {
        this.id = UUID.randomUUID();
        this.condominiumId = budget.getCondominiumId();
        this.budgetId = budget.getId();
        this.entryKey = LedgerEntryFingerprint.key(l);
        this.fileId = l.getFileId();
        this.sha256 = sha256;
        this.page = l.getPage();
        this.position = l.getSequence();
        this.date = l.getDate();
        this.accountCode = l.getAccountCode();
        this.accountName = l.getAccountName();
        this.document = l.getDocument();
        this.memo = l.getMemo();
        this.amount = l.getDebit();
        this.budgetLineId = target.getId();
        this.reallocatedBy = username;
        this.reallocatedAt = at;
    }

    public void undo(String username, Instant at) {
        if (undoneAt != null) {
            throw new IllegalStateException("Realocação já desfeita");
        }
        this.undoneBy = username;
        this.undoneAt = at;
    }

    public boolean active() {
        return undoneAt == null;
    }

    /** As the calculation uses it (matching by key). */
    public BudgetVsActualCalculator.ReallocatedEntry forCalculation() {
        return new BudgetVsActualCalculator.ReallocatedEntry(id, entryKey, fileId, date, accountCode, amount, page,
                budgetLineId, reallocatedBy, reallocatedAt);
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

    public String getEntryKey() {
        return entryKey;
    }

    public UUID getFileId() {
        return fileId;
    }

    public String getSha256() {
        return sha256;
    }

    public int getPage() {
        return page;
    }

    public int getPosition() {
        return position;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getAccountCode() {
        return accountCode;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getDocument() {
        return document;
    }

    public String getMemo() {
        return memo;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public UUID getBudgetLineId() {
        return budgetLineId;
    }

    public String getReallocatedBy() {
        return reallocatedBy;
    }

    public Instant getReallocatedAt() {
        return reallocatedAt;
    }

    public String getUndoneBy() {
        return undoneBy;
    }

    public Instant getUndoneAt() {
        return undoneAt;
    }
}
