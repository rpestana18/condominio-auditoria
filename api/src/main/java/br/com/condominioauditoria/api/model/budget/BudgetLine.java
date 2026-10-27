package br.com.condominioauditoria.api.model.budget;

import br.com.condominioauditoria.api.model.enums.BudgetLineMark;
import br.com.condominioauditoria.api.model.enums.BudgetLineReviewStatus;
import br.com.condominioauditoria.api.model.enums.BudgetLineType;
import br.com.condominioauditoria.api.model.enums.SpendingProfile;
import br.com.condominioauditoria.api.model.enums.SpendingProfileStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Budget line as printed, with source file, page and hash (RF-03.1.1). "%" and Observações are read text and do not
 * enter calculations. The line is identified by its id, in the order of {@code position} (ADR 0007, Decision 1): the
 * id is the account mapping, reallocation and budget item target. The effective code is only a display label; when the
 * Admin gives none, it is the printed code. Nothing in the calculations uses the code as a key.
 */
@Entity
public class BudgetLine {

    @Id
    private UUID id;
    private UUID budgetId;
    private UUID condominiumId;
    private UUID fileId;
    private String sha256;
    private int position;
    private int page;
    @Enumerated(EnumType.STRING)
    private BudgetLineType type;
    private String printedCode;
    private String effectiveCode;
    private String account;
    private String accountText;
    @Enumerated(EnumType.STRING)
    private BudgetLineMark mark;
    private String description;
    private BigDecimal previousBudgeted;
    private BigDecimal budgeted;
    private String percentageText;
    private String notes;
    /** RF-13.3: null = no suggestion stored yet (the query suggests on the fly). Only CONFIRMED is used. */
    @Enumerated(EnumType.STRING)
    private SpendingProfile spendingProfile;
    @Enumerated(EnumType.STRING)
    private SpendingProfileStatus spendingProfileStatus;
    private String spendingProfileReason;
    private String spendingProfileChangedBy;
    private Instant spendingProfileChangedAt;
    /** RF-13.12: a person reviewed the line against the document (ADR 0009, Decision 1). */
    @Enumerated(EnumType.STRING)
    private BudgetLineReviewStatus reviewStatus = BudgetLineReviewStatus.PENDING;
    private String reviewedBy;
    private Instant reviewedAt;
    /** O2: the line cites a group account (marked by the Admin); a specific line wins in the account mapping. */
    private boolean groupAccount;
    /** O1: the line counts by its description, not by the printed account (marked by the Admin). */
    private boolean descriptionPrevails;

    protected BudgetLine() {
    }

    public BudgetLine(Budget budget, int position, int page, BudgetLineType type, String printedCode,
            String account, String accountText, BudgetLineMark mark, String description, BigDecimal previousBudgeted,
            BigDecimal budgeted, String percentageText, String notes) {
        this.id = UUID.randomUUID();
        this.budgetId = budget.getId();
        this.condominiumId = budget.getCondominiumId();
        this.fileId = budget.getFileId();
        this.sha256 = budget.getSha256();
        this.position = position;
        this.page = page;
        this.type = type;
        this.printedCode = printedCode;
        this.effectiveCode = printedCode;
        this.account = account;
        this.accountText = accountText;
        this.mark = mark;
        this.description = description;
        this.previousBudgeted = previousBudgeted;
        this.budgeted = budgeted;
        this.percentageText = percentageText;
        this.notes = notes;
    }

    /**
     * Optional display label (RF-13.2). Blank or null = the printed code. The read value does not change.
     */
    public void setEffectiveCode(String code) {
        this.effectiveCode = code == null || code.isBlank() ? printedCode : code.trim();
    }

    /** Spending profile suggested or confirmed (RF-13.3). Never changes the planned amount. */
    public void setSpendingProfile(SpendingProfile profile, SpendingProfileStatus status, String reason,
            String username, Instant at) {
        this.spendingProfile = profile;
        this.spendingProfileStatus = status;
        this.spendingProfileReason = reason;
        this.spendingProfileChangedBy = username;
        this.spendingProfileChangedAt = at;
    }

    /** Review state (RF-13.12): REVIEWED records who and when; PENDING clears them. */
    public void setReview(BudgetLineReviewStatus status, String username, Instant at) {
        this.reviewStatus = status;
        this.reviewedBy = status == BudgetLineReviewStatus.REVIEWED ? username : null;
        this.reviewedAt = status == BudgetLineReviewStatus.REVIEWED ? at : null;
    }

    /** The Admin's marks of the review (O1 and O2). */
    public void setMarks(boolean groupAccount, boolean descriptionPrevails) {
        this.groupAccount = groupAccount;
        this.descriptionPrevails = descriptionPrevails;
    }

    public BudgetLineReviewStatus getReviewStatus() {
        return reviewStatus == null ? BudgetLineReviewStatus.PENDING : reviewStatus;
    }

    public boolean isReviewed() {
        return getReviewStatus() == BudgetLineReviewStatus.REVIEWED;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public boolean isGroupAccount() {
        return groupAccount;
    }

    public boolean isDescriptionPrevails() {
        return descriptionPrevails;
    }

    public SpendingProfile getSpendingProfile() {
        return spendingProfile;
    }

    public SpendingProfileStatus getSpendingProfileStatus() {
        return spendingProfileStatus;
    }

    public String getSpendingProfileReason() {
        return spendingProfileReason;
    }

    public String getSpendingProfileChangedBy() {
        return spendingProfileChangedBy;
    }

    public Instant getSpendingProfileChangedAt() {
        return spendingProfileChangedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBudgetId() {
        return budgetId;
    }

    public UUID getCondominiumId() {
        return condominiumId;
    }

    public UUID getFileId() {
        return fileId;
    }

    public String getSha256() {
        return sha256;
    }

    public int getPosition() {
        return position;
    }

    public int getPage() {
        return page;
    }

    public BudgetLineType getType() {
        return type;
    }

    public String getPrintedCode() {
        return printedCode;
    }

    public String getEffectiveCode() {
        return effectiveCode;
    }

    public String getAccount() {
        return account;
    }

    public String getAccountText() {
        return accountText;
    }

    public BudgetLineMark getMark() {
        return mark;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPreviousBudgeted() {
        return previousBudgeted;
    }

    public BigDecimal getBudgeted() {
        return budgeted;
    }

    public String getPercentageText() {
        return percentageText;
    }

    public String getNotes() {
        return notes;
    }
}
