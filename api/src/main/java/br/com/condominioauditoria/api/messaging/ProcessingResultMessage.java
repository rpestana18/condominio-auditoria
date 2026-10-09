package br.com.condominioauditoria.api.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Message rag → api. Contract: contracts/mensagens/v2/resultado-processamento.schema.json. These records belong to the
 * api: the rag has its own. Only the JSON contract is shared.
 */
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
        @JsonProperty("previsaoOrcamentaria") BudgetData budget,
        @JsonProperty("conferencias") List<TotalsCheckData> totalsChecks) {

    public enum Status {
        INICIADO, CONCLUIDO, FALHOU
    }

    public record CashFlow(
            @JsonProperty("empreendimento") String property,
            @JsonProperty("periodoInicio") LocalDate periodStart,
            @JsonProperty("periodoFim") LocalDate periodEnd,
            @JsonProperty("secoes") List<Section> sections,
            @JsonProperty("posicaoFinanceira") List<FundPosition> financialPosition,
            @JsonProperty("totalPosicao") FundPosition positionTotal) {

        public int entryCount() {
            return sections.stream().mapToInt(s -> s.entries().size()).sum();
        }
    }

    public record Section(
            @JsonProperty("fundo") String fund,
            @JsonProperty("saldoAnterior") BigDecimal openingBalance,
            @JsonProperty("lancamentos") List<LedgerEntryData> entries,
            @JsonProperty("totalCreditosInformado") BigDecimal reportedCreditTotal,
            @JsonProperty("totalDebitosInformado") BigDecimal reportedDebitTotal) {
    }

    public record LedgerEntryData(
            @JsonProperty("pagina") int page,
            @JsonProperty("ordem") int sequence,
            @JsonProperty("data") LocalDate date,
            @JsonProperty("contaCodigo") String accountCode,
            @JsonProperty("contaNome") String accountName,
            @JsonProperty("documento") String document,
            @JsonProperty("historico") String memo,
            @JsonProperty("credito") BigDecimal credit,
            @JsonProperty("debito") BigDecimal debit,
            @JsonProperty("saldo") BigDecimal balance,
            @JsonProperty("enriquecimento") Enrichment enrichment) {
    }

    public record Enrichment(
            @JsonProperty("notaFiscal") String invoiceNumber,
            @JsonProperty("fornecedor") String supplier,
            @JsonProperty("meioPagamento") String paymentMethod,
            @JsonProperty("transferenciaEntreFundos") boolean interFundTransfer,
            @JsonProperty("recebimentoCota") boolean condoFeeReceipt) {
    }

    /** Budget as read, as it is in the document. {@code budgetColumns} comes in the order [previous, fiscal year]. */
    public record BudgetData(
            @JsonProperty("titulo") String title,
            @JsonProperty("exercicioImpresso") String printedFiscalYear,
            @JsonProperty("colunasOrcado") List<String> budgetColumns,
            @JsonProperty("linhas") List<BudgetLineData> lines) {
    }

    public enum BudgetLineType {
        TOTAL, GRUPO, LINHA
    }

    public enum BudgetLineMark {
        RATEIO_A_PARTE, NEGOCIADA_ISENCAO, SEM_VALOR, VALOR_FIXO_SEM_REFERENCIA
    }

    /** A budget line. {@code sequence} tells apart lines with the same printed code. "%" and Observações are text. */
    public record BudgetLineData(
            @JsonProperty("ordem") int sequence,
            @JsonProperty("pagina") int page,
            @JsonProperty("tipo") BudgetLineType type,
            @JsonProperty("codigoImpresso") String printedCode,
            @JsonProperty("conta") String account,
            @JsonProperty("contaTexto") String accountText,
            @JsonProperty("marca") BudgetLineMark mark,
            @JsonProperty("descricao") String description,
            @JsonProperty("orcadoAnterior") BigDecimal previousBudgeted,
            @JsonProperty("orcado") BigDecimal budgeted,
            @JsonProperty("percentualTexto") String percentageText,
            @JsonProperty("observacoes") String notes) {
    }

    public record FundPosition(
            @JsonProperty("fundo") String fund,
            @JsonProperty("saldoAnterior") BigDecimal openingBalance,
            @JsonProperty("creditos") BigDecimal credits,
            @JsonProperty("debitos") BigDecimal debits,
            @JsonProperty("saldoAtual") BigDecimal closingBalance) {
    }

    public record TotalsCheckData(
            @JsonProperty("codigo") String code,
            @JsonProperty("descricao") String description,
            boolean ok,
            @JsonProperty("detalhe") String detail) {
    }
}
