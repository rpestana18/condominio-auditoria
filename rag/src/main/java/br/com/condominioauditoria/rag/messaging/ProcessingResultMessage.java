package br.com.condominioauditoria.rag.messaging;

import br.com.condominioauditoria.rag.model.TotalsCheck;
import br.com.condominioauditoria.rag.model.budget.Budget;
import br.com.condominioauditoria.rag.model.cashflow.CashFlow;
import java.util.List;
import java.util.UUID;

/** Message rag → api. Contract: contracts/mensagens/v3/processing-result.schema.json. */
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
        List<TotalsCheck> totalsChecks) {

    /** Contract version published by the rag. The api accepts only v3 (ADR 0006, phase 2). */
    public static final int VERSION = 3;

    public enum Status {
        STARTED, COMPLETED, FAILED
    }

    public static ProcessingResultMessage started(FileReceivedMessage a) {
        return new ProcessingResultMessage(VERSION, a.processingId(), a.fileId(), a.condominiumId(),
                Status.STARTED, null, null, null, null, null, null);
    }

    public static ProcessingResultMessage failed(FileReceivedMessage a, String reason) {
        return new ProcessingResultMessage(VERSION, a.processingId(), a.fileId(), a.condominiumId(),
                Status.FAILED, reason, null, null, null, null, null);
    }

    /** Cash flow read, or a layout without a parser yet (null cash flow). */
    public static ProcessingResultMessage completed(FileReceivedMessage a, String parser, int pages,
            CashFlow cashFlow, List<TotalsCheck> checks) {
        return new ProcessingResultMessage(VERSION, a.processingId(), a.fileId(), a.condominiumId(),
                Status.COMPLETED, null, parser, pages, cashFlow, null, checks);
    }

    /** Budget read. */
    public static ProcessingResultMessage completedBudget(FileReceivedMessage a, String parser, int pages,
            Budget budgetData, List<TotalsCheck> checks) {
        return new ProcessingResultMessage(VERSION, a.processingId(), a.fileId(), a.condominiumId(),
                Status.COMPLETED, null, parser, pages, null, budgetData, checks);
    }
}
