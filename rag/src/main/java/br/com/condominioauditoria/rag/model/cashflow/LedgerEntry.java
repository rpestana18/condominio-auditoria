package br.com.condominioauditoria.rag.model.cashflow;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A cash flow line, as it is in the management company's report, with its origin (page and position) and the fields
 * enriched from the memo.
 */
public record LedgerEntry(
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

    /**
     * Information deduced from the memo. Kept apart to make clear what is original and what is inferred.
     * {@code condoFeeReceipt}: "RECIBOS ACUMULADOS" credit of the Protest layout (ADR 0004, Decision 7).
     */
    public record Enrichment(
            @JsonProperty("notaFiscal") String invoiceNumber,
            @JsonProperty("fornecedor") String supplier,
            @JsonProperty("meioPagamento") String paymentMethod,
            @JsonProperty("transferenciaEntreFundos") boolean interFundTransfer,
            @JsonProperty("recebimentoCota") boolean condoFeeReceipt) {
    }
}
