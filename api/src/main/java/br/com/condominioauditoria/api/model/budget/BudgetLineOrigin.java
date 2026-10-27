package br.com.condominioauditoria.api.model.budget;

import br.com.condominioauditoria.api.model.enums.OriginMethod;
import br.com.condominioauditoria.api.model.enums.OriginSource;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Where the amount of a budget line came from (RF-13.13; ADR 0009, Decision 3 B), 1:1 with the line: the support
 * sheet read by the rag ({@link OriginSource#SHEET}, with file, sheet, row and hash) or the Admin. It only explains:
 * the planned amount is always the budget's (O4), and nothing here enters a calculation.
 */
@Entity
public class BudgetLineOrigin {

    @Id
    private UUID id;
    private UUID budgetLineId;
    private UUID budgetId;
    private UUID condominiumId;
    private String sourceAccounts;
    private LocalDate averageFrom;
    private LocalDate averageTo;
    private BigDecimal averageAmount;
    private BigDecimal baseAmount;
    @Enumerated(EnumType.STRING)
    private OriginMethod method;
    private String indexName;
    private BigDecimal indexRate;
    private boolean indexForecast;
    private String indexNote;
    private BigDecimal finalAmount;
    private String reason;
    @Enumerated(EnumType.STRING)
    private OriginSource source;
    private UUID fileId;
    private String fileName;
    private String sheet;
    private Integer sheetRow;
    private String sha256;
    private String changedBy;
    private Instant changedAt;

    protected BudgetLineOrigin() {
    }

    public BudgetLineOrigin(BudgetLine line) {
        this.id = UUID.randomUUID();
        this.budgetLineId = line.getId();
        this.budgetId = line.getBudgetId();
        this.condominiumId = line.getCondominiumId();
    }

    /** The fields of the origin, all at once (sheet read or Admin correction). */
    public void set(List<String> sourceAccounts, YearMonth averageFrom, YearMonth averageTo, BigDecimal averageAmount,
            BigDecimal baseAmount, OriginMethod method, String indexName, BigDecimal indexRate, boolean indexForecast,
            String indexNote, BigDecimal finalAmount, String reason) {
        this.sourceAccounts = sourceAccounts == null || sourceAccounts.isEmpty() ? null
                : String.join(",", sourceAccounts);
        this.averageFrom = averageFrom == null ? null : averageFrom.atDay(1);
        this.averageTo = averageTo == null ? null : averageTo.atDay(1);
        this.averageAmount = averageAmount;
        this.baseAmount = baseAmount;
        this.method = method;
        this.indexName = indexName;
        this.indexRate = indexRate;
        this.indexForecast = indexForecast;
        this.indexNote = indexNote;
        this.finalAmount = finalAmount;
        this.reason = reason;
    }

    /** Where it came from: the sheet (file, sheet, row, hash) or the Admin (no file). */
    public void source(OriginSource source, UUID fileId, String fileName, String sheet, Integer sheetRow,
            String sha256, String username, Instant at) {
        this.source = source;
        this.fileId = fileId;
        this.fileName = fileName;
        this.sheet = sheet;
        this.sheetRow = sheetRow;
        this.sha256 = sha256;
        this.changedBy = username;
        this.changedAt = at;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBudgetLineId() {
        return budgetLineId;
    }

    public UUID getBudgetId() {
        return budgetId;
    }

    public List<String> getSourceAccounts() {
        return sourceAccounts == null || sourceAccounts.isBlank() ? List.of()
                : Arrays.stream(sourceAccounts.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public YearMonth getAverageFrom() {
        return averageFrom == null ? null : YearMonth.from(averageFrom);
    }

    public YearMonth getAverageTo() {
        return averageTo == null ? null : YearMonth.from(averageTo);
    }

    public BigDecimal getAverageAmount() {
        return averageAmount;
    }

    public BigDecimal getBaseAmount() {
        return baseAmount;
    }

    public OriginMethod getMethod() {
        return method;
    }

    public String getIndexName() {
        return indexName;
    }

    public BigDecimal getIndexRate() {
        return indexRate;
    }

    public boolean isIndexForecast() {
        return indexForecast;
    }

    public String getIndexNote() {
        return indexNote;
    }

    public BigDecimal getFinalAmount() {
        return finalAmount;
    }

    public String getReason() {
        return reason;
    }

    public OriginSource getSource() {
        return source;
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

    public Integer getSheetRow() {
        return sheetRow;
    }

    public String getSha256() {
        return sha256;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
