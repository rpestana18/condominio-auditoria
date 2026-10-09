package br.com.condominioauditoria.api.dto.response.file;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record FundBalanceResponse(
        @JsonProperty("fundo") String fund,
        @JsonProperty("saldoAnterior") BigDecimal openingBalance,
        @JsonProperty("creditos") BigDecimal credits,
        @JsonProperty("debitos") BigDecimal debits,
        @JsonProperty("saldoAtual") BigDecimal closingBalance) {
}
