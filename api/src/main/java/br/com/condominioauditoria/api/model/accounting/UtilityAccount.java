package br.com.condominioauditoria.api.model.accounting;

import br.com.condominioauditoria.api.model.enums.UtilityType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;

/**
 * Decision of the Manager or the Admin on a cash flow account (RF-12.1): which utility it is, or none (rejected). Only
 * decided accounts exist here; suggestions are computed on each query and never enter the charts.
 */
@Entity
public class UtilityAccount {

    @Id
    private UUID id;
    private UUID condominiumId;
    private String accountCode;
    private String accountName;
    @Enumerated(EnumType.STRING)
    private UtilityType utilityType;
    private String decidedBy;
    private Instant decidedAt;

    protected UtilityAccount() {
    }

    public UtilityAccount(UUID condominiumId, String accountCode, String accountName) {
        this.id = UUID.randomUUID();
        this.condominiumId = condominiumId;
        this.accountCode = accountCode;
        this.accountName = accountName;
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

    public String getAccountName() {
        return accountName;
    }

    /** Null when the account was rejected. */
    public UtilityType getUtilityType() {
        return utilityType;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    /** Confirms the account as {@code type}, or rejects it with {@code null}. */
    public void decide(UtilityType type, String username, Instant at) {
        this.utilityType = type;
        this.decidedBy = username;
        this.decidedAt = at;
    }
}
