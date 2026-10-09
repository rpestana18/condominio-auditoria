package br.com.condominioauditoria.rag.model.cashflow;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/** A line of the "Posição Financeira" table at the end of the report. */
public record FundPosition(
        @JsonProperty("fundo") String fund,
        @JsonProperty("saldoAnterior") BigDecimal openingBalance,
        @JsonProperty("creditos") BigDecimal credits,
        @JsonProperty("debitos") BigDecimal debits,
        @JsonProperty("saldoAtual") BigDecimal closingBalance) {
}
