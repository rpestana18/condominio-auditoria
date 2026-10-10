package br.com.condominioauditoria.api.model.budget;

import br.com.condominioauditoria.api.model.enums.BudgetStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Budget read from a file of the PO category (ADR 0004, Decision 3). One per file: reprocessing updates this same row
 * while the budget is not confirmed. Source file and hash are kept here and on each line.
 */
@Entity
public class Budget {

    @Id
    private UUID id;
    private UUID condominiumId;
    private UUID fileId;
    private String sha256;
    private String parser;
    private String title;
    private String printedFiscalYear;
    private String previousBudgetedColumn;
    private String budgetedColumn;
    @Enumerated(EnumType.STRING)
    private BudgetStatus status;
    private BigDecimal printedTotal;
    private BigDecimal printedMonthlyPlanned;
    private BigDecimal monthlyPlanned;
    private BigDecimal roundingTolerance;
    private Instant readAt;
    private Integer version;
    private LocalDate fiscalYearStart;
    private LocalDate fiscalYearEnd;
    private LocalDate supersededFrom;
    private UUID minutesFileId;
    private boolean withoutMinutes;
    private LocalDate approvalDate;
    private boolean discrepancyAcknowledged;
    private String discrepancyJustification;
    private String confirmedBy;
    private Instant confirmedAt;
    private LocalDate extendedUntil;
    private String extensionJustification;
    private String extendedBy;
    private Instant extendedAt;

    protected Budget() {
    }

    public Budget(UUID condominiumId, UUID fileId, String sha256) {
        this.id = UUID.randomUUID();
        this.condominiumId = condominiumId;
        this.fileId = fileId;
        this.sha256 = sha256;
    }

    /** New reading of the same file (only before confirmation). */
    public void recordReading(String parser, String title, String printedFiscalYear,
            String previousBudgetedColumn, String budgetedColumn, BudgetStatus status, BigDecimal printedTotal,
            BigDecimal printedMonthlyPlanned, BigDecimal monthlyPlanned, BigDecimal tolerance, Instant at) {
        if (this.status != null && this.status.isLocked()) {
            throw new IllegalStateException("PO já confirmada: a leitura não pode ser trocada");
        }
        if (status.isLocked()) {
            throw new IllegalArgumentException("A leitura só gera os estados READ e READ_WITH_DISCREPANCY");
        }
        this.parser = parser;
        this.title = title;
        this.printedFiscalYear = printedFiscalYear;
        this.previousBudgetedColumn = previousBudgetedColumn;
        this.budgetedColumn = budgetedColumn;
        this.status = status;
        this.printedTotal = printedTotal;
        this.printedMonthlyPlanned = printedMonthlyPlanned;
        this.monthlyPlanned = monthlyPlanned;
        this.roundingTolerance = tolerance;
        this.readAt = at;
    }

    /** RF-03.1.3: confirmation by the Admin. Validations live in the confirmation service. */
    public void confirm(int version, YearMonth start, YearMonth end, UUID minutesFileId, boolean withoutMinutes,
            LocalDate approvalDate, boolean discrepancyAcknowledged, String justification, String username,
                    Instant at) {
        if (status.isLocked()) {
            throw new IllegalStateException("A PO já foi confirmada");
        }
        this.version = version;
        this.fiscalYearStart = start.atDay(1);
        this.fiscalYearEnd = end.atDay(1);
        this.minutesFileId = minutesFileId;
        this.withoutMinutes = withoutMinutes;
        this.approvalDate = approvalDate;
        this.discrepancyAcknowledged = discrepancyAcknowledged;
        this.discrepancyJustification = justification;
        this.confirmedBy = username;
        this.confirmedAt = at;
        this.status = BudgetStatus.CONFIRMED;
    }

    /** Reapproval: from {@code month} on, the new version applies; the earlier months keep this one. */
    public void supersedeFrom(YearMonth month) {
        if (!status.isLocked()) {
            throw new IllegalStateException("Só PO confirmada pode ser substituída");
        }
        LocalDate day = month.atDay(1);
        if (supersededFrom == null || day.isBefore(supersededFrom)) {
            supersededFrom = day;
        }
        status = BudgetStatus.SUPERSEDED;
    }

    /**
     * RF-11.3: the budget is also valid in the months after the fiscal year until {@code until}, with a justification.
     * The validations (Admin, no month with a confirmed budget) live in the extension service.
     */
    public void extend(YearMonth until, String justification, String username, Instant at) {
        if (status != BudgetStatus.CONFIRMED) {
            throw new IllegalStateException("Só PO confirmada (e não substituída) pode ser prorrogada");
        }
        if (!until.isAfter(getFiscalYearEnd())) {
            throw new IllegalArgumentException("A prorrogação termina depois do fim do exercício");
        }
        this.extendedUntil = until.atDay(1);
        this.extensionJustification = justification;
        this.extendedBy = username;
        this.extendedAt = at;
    }

    public void undoExtension() {
        this.extendedUntil = null;
        this.extensionJustification = null;
        this.extendedBy = null;
        this.extendedAt = null;
    }

    /**
     * New budget confirmed over extended months (ADR 0005, Decision 4): the extension now ends in {@code until}; if
     * {@code until} is not after the end of the fiscal year, the extension ends.
     */
    public void shortenExtension(YearMonth until) {
        if (extendedUntil == null) {
            return;
        }
        if (!until.isAfter(getFiscalYearEnd())) {
            undoExtension();
        } else if (until.isBefore(getExtendedUntil())) {
            this.extendedUntil = until.atDay(1);
        }
    }

    public UUID getId() {
        return id;
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

    public String getParser() {
        return parser;
    }

    public String getTitle() {
        return title;
    }

    public String getPrintedFiscalYear() {
        return printedFiscalYear;
    }

    public String getPreviousBudgetedColumn() {
        return previousBudgetedColumn;
    }

    public String getBudgetedColumn() {
        return budgetedColumn;
    }

    public BudgetStatus getStatus() {
        return status;
    }

    public BigDecimal getPrintedTotal() {
        return printedTotal;
    }

    public BigDecimal getPrintedMonthlyPlanned() {
        return printedMonthlyPlanned;
    }

    public BigDecimal getMonthlyPlanned() {
        return monthlyPlanned;
    }

    public BigDecimal getRoundingTolerance() {
        return roundingTolerance;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public Integer getVersion() {
        return version;
    }

    public YearMonth getFiscalYearStart() {
        return fiscalYearStart == null ? null : YearMonth.from(fiscalYearStart);
    }

    public YearMonth getFiscalYearEnd() {
        return fiscalYearEnd == null ? null : YearMonth.from(fiscalYearEnd);
    }

    public YearMonth getSupersededFrom() {
        return supersededFrom == null ? null : YearMonth.from(supersededFrom);
    }

    public UUID getMinutesFileId() {
        return minutesFileId;
    }

    public boolean isWithoutMinutes() {
        return withoutMinutes;
    }

    public LocalDate getApprovalDate() {
        return approvalDate;
    }

    public boolean isDiscrepancyAcknowledged() {
        return discrepancyAcknowledged;
    }

    public String getDiscrepancyJustification() {
        return discrepancyJustification;
    }

    public String getConfirmedBy() {
        return confirmedBy;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    /** Last extended month, or null without an extension. */
    public YearMonth getExtendedUntil() {
        return extendedUntil == null ? null : YearMonth.from(extendedUntil);
    }

    public String getExtensionJustification() {
        return extensionJustification;
    }

    public String getExtendedBy() {
        return extendedBy;
    }

    public Instant getExtendedAt() {
        return extendedAt;
    }
}
