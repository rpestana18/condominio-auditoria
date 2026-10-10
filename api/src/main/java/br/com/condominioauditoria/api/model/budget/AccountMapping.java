package br.com.condominioauditoria.api.model.budget;

import br.com.condominioauditoria.api.model.enums.AccountMappingSource;
import br.com.condominioauditoria.api.model.enums.AccountMappingStatus;
import br.com.condominioauditoria.api.model.enums.MappingTargetType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;

/**
 * Mapping of a cash flow account in a budget version (RF-03.1.4): exactly one target per account (unique key per budget
 * and account). Every change writes an {@link AccountMappingEvent} to the trail, which is insert-only.
 */
@Entity
public class AccountMapping {

    @Id
    private UUID id;
    private UUID condominiumId;
    private UUID budgetId;
    private String accountCode;
    private String accountName;
    @Enumerated(EnumType.STRING)
    private MappingTargetType targetType;
    private UUID budgetLineId;
    private String targetDetail;
    @Enumerated(EnumType.STRING)
    private AccountMappingStatus status;
    @Enumerated(EnumType.STRING)
    private AccountMappingSource source;
    private String reason;
    private boolean sameAsPreviousVersion;
    private String updatedBy;
    private Instant updatedAt;

    protected AccountMapping() {
    }

    public AccountMapping(Budget budget, String accountCode, String accountName, MappingTarget target,
            AccountMappingStatus status, AccountMappingSource source, String reason, boolean sameAsPreviousVersion,
                    String username,
            Instant at) {
        this.id = UUID.randomUUID();
        this.condominiumId = budget.getCondominiumId();
        this.budgetId = budget.getId();
        this.accountCode = accountCode;
        this.accountName = accountName;
        change(target, status, source, reason, sameAsPreviousVersion, username, at);
    }

    public void change(MappingTarget target, AccountMappingStatus status, AccountMappingSource source, String reason,
            boolean sameAsPreviousVersion, String username, Instant at) {
        this.targetType = target.type();
        this.budgetLineId = target.budgetLineId();
        this.targetDetail = target.detail();
        this.status = status;
        this.source = source;
        this.reason = reason;
        this.sameAsPreviousVersion = sameAsPreviousVersion;
        this.updatedBy = username;
        this.updatedAt = at;
    }

    /** Only the status changes (confirm or reject); target, source and reason stay. */
    public void changeStatus(AccountMappingStatus newStatus, String username, Instant at) {
        this.status = newStatus;
        this.updatedBy = username;
        this.updatedAt = at;
    }

    public void updateName(String name) {
        if (name != null && !name.isBlank()) {
            this.accountName = name;
        }
    }

    /** Target without the line text (for the text, use {@link #target(java.util.Map)}). */
    public MappingTarget target() {
        return new MappingTarget(targetType, budgetLineId, null, null, targetDetail);
    }

    public MappingTarget target(java.util.Map<UUID, BudgetLine> lines) {
        BudgetLine l = budgetLineId == null ? null : lines.get(budgetLineId);
        return l == null ? target() : new MappingTarget(targetType, budgetLineId, l.getEffectiveCode(),
                l.getDescription(), null);
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

    public MappingTargetType getTargetType() {
        return targetType;
    }

    public UUID getBudgetLineId() {
        return budgetLineId;
    }

    public String getTargetDetail() {
        return targetDetail;
    }

    public AccountMappingStatus getStatus() {
        return status;
    }

    public AccountMappingSource getSource() {
        return source;
    }

    public String getReason() {
        return reason;
    }

    public boolean isSameAsPreviousVersion() {
        return sameAsPreviousVersion;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
