package br.com.condominioauditoria.api.model.budget;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;

/**
 * One account of the expenses sheet of the budget's support sheet (contracts/mensagens/v5; ADR 0009, Decision 8), in
 * the budget's chart of accounts: code, printed name, row and the group account deduced by the rag from the sums. It is
 * the bridge of the automatic account mapping (Q54): a cash flow account with the same name is tied to the budget line
 * whose origin cites this account or its group. The number is never compared with the cash flow's (RF-00.8).
 */
@Entity
public class BudgetSheetAccount {

    @Id
    private UUID id;
    private UUID condominiumId;
    private UUID budgetId;
    private String accountCode;
    private String accountName;
    private String parentCode;
    private UUID fileId;
    private String fileName;
    private String sheet;
    private int sheetRow;
    private String sha256;
    private Instant savedAt;

    protected BudgetSheetAccount() {
    }

    public BudgetSheetAccount(Budget budget, String accountCode, String accountName, String parentCode, UUID fileId,
            String fileName, String sheet, int sheetRow, String sha256, Instant savedAt) {
        this.id = UUID.randomUUID();
        this.condominiumId = budget.getCondominiumId();
        this.budgetId = budget.getId();
        this.accountCode = accountCode;
        this.accountName = accountName;
        this.parentCode = parentCode;
        this.fileId = fileId;
        this.fileName = fileName;
        this.sheet = sheet;
        this.sheetRow = sheetRow;
        this.sha256 = sha256;
        this.savedAt = savedAt;
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

    public String getAccountCode() {
        return accountCode;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getParentCode() {
        return parentCode;
    }

    public UUID getFileId() {
        return fileId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getSheet() {
        return sheet;
    }

    public int getSheetRow() {
        return sheetRow;
    }

    public String getSha256() {
        return sha256;
    }

    public Instant getSavedAt() {
        return savedAt;
    }
}
