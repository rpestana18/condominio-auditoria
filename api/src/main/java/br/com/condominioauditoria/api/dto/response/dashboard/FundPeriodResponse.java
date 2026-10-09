package br.com.condominioauditoria.api.dto.response.dashboard;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

public record FundPeriodResponse(
        @JsonProperty("fundoId") UUID fundId,
        @JsonProperty("fundo") String fund,
        @JsonProperty("saldoAnterior") BigDecimal openingBalance,
        @JsonProperty("entradas") BigDecimal inflows,
        @JsonProperty("saidas") BigDecimal outflows,
        @JsonProperty("resultado") BigDecimal result,
        @JsonProperty("saldoAtual") BigDecimal closingBalance) {
}
