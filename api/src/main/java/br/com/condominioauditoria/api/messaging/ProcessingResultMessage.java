package br.com.condominioauditoria.api.messaging;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Message rag → api. Contract: contracts/mensagens/v5/processing-result.schema.json
 * (v5: the accounts of the support sheet's expenses sheet, ADR 0009 Decision 8; v4: {@code budgetLineOrigins}, the origin of the lines read from the budget's support sheet, ADR 0009 Decision 3 B). These records belong to the
 * api: the rag has its own. Only the JSON contract is shared.
 */
public record ProcessingResultMessage(
        int version,
        UUID processingId,
        UUID fileId,
        UUID condominiumId,
        Status status,
        String reason,
        String parser,
        Integer pages,
        CashFlow cashFlow,
        BudgetData budget,
        List<TotalsCheckData> totalsChecks,
        BudgetLineOriginsData budgetLineOrigins) {

    /** Message without the origins of the lines (cash flow and budget results). */
    public ProcessingResultMessage(int version, UUID processingId, UUID fileId, UUID condominiumId, Status status,
            String reason, String parser, Integer pages, CashFlow cashFlow, BudgetData budget,
            List<TotalsCheckData> totalsChecks) {
        this(version, processingId, fileId, condominiumId, status, reason, parser, pages, cashFlow, budget,
                totalsChecks, null);
    }

    public enum Status {
        STARTED, COMPLETED, FAILED
    }

    public record CashFlow(
            String property,
            LocalDate periodStart,
            LocalDate periodEnd,
            List<Section> sections,
            List<FundPosition> financialPosition,
            FundPosition positionTotal) {

        public int entryCount() {
            return sections.stream().mapToInt(s -> s.entries().size()).sum();
        }
    }

    public record Section(
            String fund,
            BigDecimal openingBalance,
            List<LedgerEntryData> entries,
            BigDecimal reportedCreditTotal,
            BigDecimal reportedDebitTotal) {
    }

    public record LedgerEntryData(
            int page,
            int sequence,
            LocalDate date,
            String accountCode,
            String accountName,
            String document,
            String memo,
            BigDecimal credit,
            BigDecimal debit,
            BigDecimal balance,
            Enrichment enrichment) {
    }

    public record Enrichment(
            String invoiceNumber,
            String supplier,
            String paymentMethod,
            boolean interFundTransfer,
            boolean condoFeeReceipt) {
    }

    /** Budget as read, as it is in the document. {@code budgetColumns} comes in the order [previous, fiscal year]. */
    public record BudgetData(
            String title,
            String printedFiscalYear,
            List<String> budgetColumns,
            List<BudgetLineData> lines) {
    }

    public enum BudgetLineType {
        TOTAL, GROUP, LINE
    }

    public enum BudgetLineMark {
        SEPARATE_APPORTIONMENT, NEGOTIATED_EXEMPTION, NO_AMOUNT, FIXED_AMOUNT_NO_REFERENCE
    }

    /** A budget line. {@code sequence} tells apart lines with the same printed code. "%" and Observações are text. */
    public record BudgetLineData(
            int sequence,
            int page,
            BudgetLineType type,
            String printedCode,
            String account,
            String accountText,
            BudgetLineMark mark,
            String description,
            BigDecimal previousBudgeted,
            BigDecimal budgeted,
            String percentageText,
            String notes) {
    }

    public record FundPosition(
            String fund,
            BigDecimal openingBalance,
            BigDecimal credits,
            BigDecimal debits,
            BigDecimal closingBalance) {
    }

    public record TotalsCheckData(
            String code,
            String description,
            boolean ok,
            String detail) {
    }

    /**
     * Lines of the budget's support sheet (v4): where each amount came from. Explains only, never enters a calculation.
     * {@code accounts} (v5): the accounts of the expenses sheet, the bridge of the automatic account mapping (Q54).
     */
    public record BudgetLineOriginsData(
            String sheet,
            String expensesSheet,
            String indexSheet,
            List<BudgetLineOriginData> lines,
            List<ExpenseAccountData> accounts) {
    }

    /**
     * One account of the expenses sheet (v5), in the budget's chart of accounts: code, printed name, row and the group
     * account deduced by the rag from the sums (null when none).
     */
    public record ExpenseAccountData(
            String code,
            String name,
            int row,
            String parentCode) {
    }

    /**
     * One expense line of the support sheet. {@code sequence} is the order among the expense lines, the same order as
     * the budget's expense lines. Months as "yyyy-MM"; rate as a decimal fraction ("0.0723").
     */
    public record BudgetLineOriginData(
            int sequence,
            int row,
            String printedCode,
            String account,
            String description,
            List<String> sourceAccounts,
            String averageFrom,
            String averageTo,
            BigDecimal averageAmount,
            BigDecimal baseAmount,
            String method,
            String indexName,
            BigDecimal indexRate,
            boolean indexForecast,
            String indexNote,
            BigDecimal finalAmount,
            String reason) {
    }
}
