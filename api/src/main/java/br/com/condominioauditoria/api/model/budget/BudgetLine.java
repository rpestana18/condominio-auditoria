package br.com.condominioauditoria.api.model.budget;

import br.com.condominioauditoria.api.model.enums.BudgetLineMark;
import br.com.condominioauditoria.api.model.enums.BudgetLineType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Budget line as printed, with source file, page and hash (RF-03.1.1). "%" and Observações are read text and do not
 * enter calculations. The id is the account mapping target; the effective code only differs from the printed one when
 * the code repeats.
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

    /** Distinct code for the line whose printed code repeats (RF-03.1.2). The read value does not change. */
    public void setEffectiveCode(String code) {
        this.effectiveCode = code;
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
