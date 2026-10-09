package br.com.condominioauditoria.rag.model.cashflow;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

/** A fund's block in the cash flow: opening balance, entries and the report's own TOTAIS line. */
public record FundSection(
        @JsonProperty("fundo") String fund,
        @JsonProperty("saldoAnterior") BigDecimal openingBalance,
        @JsonProperty("lancamentos") List<LedgerEntry> entries,
        @JsonProperty("totalCreditosInformado") BigDecimal reportedCreditTotal,
        @JsonProperty("totalDebitosInformado") BigDecimal reportedDebitTotal) {
}
