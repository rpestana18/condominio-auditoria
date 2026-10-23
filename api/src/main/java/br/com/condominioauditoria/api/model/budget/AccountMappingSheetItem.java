package br.com.condominioauditoria.api.model.budget;

import br.com.condominioauditoria.api.model.enums.MappingTargetType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One accepted line of the last account mapping suggestion sheet loaded in the condominium (Q51, RF-03.1.5). Only the
 * processed line is stored (never the file), with the file name, the line and the hash of origin. The budget line is
 * kept by its effective code, resolved again in each budget version.
 */
@Entity
@Table(name = "account_mapping_sheet_item")
public class AccountMappingSheetItem {

    @Id
    private UUID id;
    private UUID condominiumId;
    private String accountCode;
    @Enumerated(EnumType.STRING)
    private MappingTargetType targetType;
    private String targetLineCode;
    private String targetDetail;
    private String fileName;
    private String fileSha256;
    private int fileLine;
    private String loadedBy;
    private Instant loadedAt;

    protected AccountMappingSheetItem() {
    }

    public AccountMappingSheetItem(UUID condominiumId, String accountCode, MappingTarget target, String fileName,
            String fileSha256, int fileLine, String loadedBy, Instant loadedAt) {
        this.id = UUID.randomUUID();
        this.condominiumId = condominiumId;
        this.accountCode = accountCode;
        this.targetType = target.type();
        this.targetLineCode = target.type() == MappingTargetType.BUDGET_LINE ? target.code() : null;
        this.targetDetail = target.detail();
        this.fileName = fileName;
        this.fileSha256 = fileSha256;
        this.fileLine = fileLine;
        this.loadedBy = loadedBy;
        this.loadedAt = loadedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCondominiumId() {
        return condominiumId;
    }

    public String getAccountCode() {
        return accountCode;
    }

    public MappingTargetType getTargetType() {
        return targetType;
    }

    public String getTargetLineCode() {
        return targetLineCode;
    }

    public String getTargetDetail() {
        return targetDetail;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFileSha256() {
        return fileSha256;
    }

    public int getFileLine() {
        return fileLine;
    }

    public String getLoadedBy() {
        return loadedBy;
    }

    public Instant getLoadedAt() {
        return loadedAt;
    }
}
