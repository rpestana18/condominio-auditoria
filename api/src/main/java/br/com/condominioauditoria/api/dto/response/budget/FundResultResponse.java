package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.FundComparisonStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Reserve, works and other funds. {@code planned}, {@code collected}, {@code difference} and {@code execution} only
 * with COMPARED; {@code credits} and {@code debits} are the movement of the period.
 */
public record FundResultResponse(
        @JsonProperty("fundoId") UUID fundId,
        @JsonProperty("fundo") String fund,
        @JsonProperty("linhaId") UUID lineId,
        @JsonProperty("linhaCodigo") String lineCode,
        @JsonProperty("situacao") FundComparisonStatus status,
        @JsonProperty("previsto") BigDecimal planned,
        @JsonProperty("arrecadado") BigDecimal collected,
        @JsonProperty("diferenca") BigDecimal difference,
        @JsonProperty("execucao") BigDecimal execution,
        @JsonProperty("creditos") BigDecimal credits,
        @JsonProperty("debitos") BigDecimal debits) {
}
