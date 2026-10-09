package br.com.condominioauditoria.rag.messaging;

import br.com.condominioauditoria.rag.model.TotalsCheck;
import br.com.condominioauditoria.rag.model.budget.Budget;
import br.com.condominioauditoria.rag.model.cashflow.CashFlow;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

/** Message rag → api. Contract: contracts/mensagens/v2/resultado-processamento.schema.json. */
public record ProcessingResultMessage(
        @JsonProperty("versao") int version,
        @JsonProperty("processamentoId") UUID processingId,
        @JsonProperty("arquivoId") UUID fileId,
        @JsonProperty("condominioId") UUID condominiumId,
        @JsonProperty("situacao") Status status,
        @JsonProperty("motivo") String reason,
        @JsonProperty("interpretador") String parser,
        @JsonProperty("paginas") Integer pages,
        @JsonProperty("fluxoDeCaixa") CashFlow cashFlow,
        @JsonProperty("previsaoOrcamentaria") Budget budget,
        @JsonProperty("conferencias") List<TotalsCheck> totalsChecks) {

    /** Contract version published by the rag. The api no longer accepts v1 (ADR 0004, Decision 2). */
    public static final int VERSION = 2;

    public enum Status {
        INICIADO, CONCLUIDO, FALHOU
    }

    public static ProcessingResultMessage started(FileReceivedMessage a) {
        return new ProcessingResultMessage(VERSION, a.processingId(), a.fileId(), a.condominiumId(),
                Status.INICIADO, null, null, null, null, null, null);
    }

    public static ProcessingResultMessage failed(FileReceivedMessage a, String reason) {
        return new ProcessingResultMessage(VERSION, a.processingId(), a.fileId(), a.condominiumId(),
                Status.FALHOU, reason, null, null, null, null, null);
    }

    /** Cash flow read, or a layout without a parser yet (null cash flow). */
    public static ProcessingResultMessage completed(FileReceivedMessage a, String parser, int pages,
            CashFlow cashFlow, List<TotalsCheck> checks) {
        return new ProcessingResultMessage(VERSION, a.processingId(), a.fileId(), a.condominiumId(),
                Status.CONCLUIDO, null, parser, pages, cashFlow, null, checks);
    }

    /** Budget read. */
    public static ProcessingResultMessage completedBudget(FileReceivedMessage a, String parser, int pages,
            Budget budgetData, List<TotalsCheck> checks) {
        return new ProcessingResultMessage(VERSION, a.processingId(), a.fileId(), a.condominiumId(),
                Status.CONCLUIDO, null, parser, pages, null, budgetData, checks);
    }
}
