package br.com.condominioauditoria.api.messaging;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Message rag → api. Contract: contracts/mensagens/v3/processing-result.schema.json. These records belong to the
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
        List<TotalsCheckData> totalsChecks) {

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
}
