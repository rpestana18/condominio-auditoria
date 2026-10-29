package br.com.condominioauditoria.rag.messaging;

import br.com.condominioauditoria.rag.model.TotalsCheck;
import br.com.condominioauditoria.rag.model.budget.Budget;
import br.com.condominioauditoria.rag.model.budget.BudgetLineOrigins;
import br.com.condominioauditoria.rag.model.cashflow.CashFlow;
import java.util.List;
import java.util.UUID;

/**
 * Message rag → api. Contract: contracts/mensagens/v5/processing-result.schema.json (v5: the expense accounts of the support sheet; v4: {@code budgetLineOrigins}, the
 * origin of the lines read from the budget's support sheet; ADR 0009, Decision 3 B).
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
        Budget budget,
        List<TotalsCheck> totalsChecks,
        BudgetLineOrigins budgetLineOrigins) {

    /** Contract version published by the rag. The api accepts only v5 (ADR 0009, Decision 8). */
    public static final int VERSION = 5;

    public enum Status {
        STARTED, COMPLETED, FAILED
    }

    public static ProcessingResultMessage started(FileReceivedMessage a) {
        return new ProcessingResultMessage(VERSION, a.processingId(), a.fileId(), a.condominiumId(),
                Status.STARTED, null, null, null, null, null, null, null);
    }

    public static ProcessingResultMessage failed(FileReceivedMessage a, String reason) {
        return new ProcessingResultMessage(VERSION, a.processingId(), a.fileId(), a.condominiumId(),
                Status.FAILED, reason, null, null, null, null, null, null);
    }

    /** Cash flow read, or a layout without a parser yet (null cash flow). */
    public static ProcessingResultMessage completed(FileReceivedMessage a, String parser, int pages,
            CashFlow cashFlow, List<TotalsCheck> checks) {
        return new ProcessingResultMessage(VERSION, a.processingId(), a.fileId(), a.condominiumId(),
                Status.COMPLETED, null, parser, pages, cashFlow, null, checks, null);
    }

    /** Budget read. */
    public static ProcessingResultMessage completedBudget(FileReceivedMessage a, String parser, int pages,
            Budget budgetData, List<TotalsCheck> checks) {
        return new ProcessingResultMessage(VERSION, a.processingId(), a.fileId(), a.condominiumId(),
                Status.COMPLETED, null, parser, pages, null, budgetData, checks, null);
    }

    /** Support sheet of the budget read: the origin of each line and the expense accounts (v5). */
    public static ProcessingResultMessage completedSupportSheet(FileReceivedMessage a, String parser, int pages,
            BudgetLineOrigins origins, List<TotalsCheck> checks) {
        return new ProcessingResultMessage(VERSION, a.processingId(), a.fileId(), a.condominiumId(),
                Status.COMPLETED, null, parser, pages, null, null, checks, origins);
    }
}
