package br.com.condominioauditoria.api.dto.response.feature;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Usage row. estimatedCostUsd: absent without tokens or with the cost unavailable; null with a model without price;
 * otherwise decimal text with 2 places ("3.50"), formatted from the BigDecimal.
 */
public record UsageTotalResponse(
        @JsonProperty("mes") @JsonInclude(JsonInclude.Include.NON_NULL) String month,
        @JsonProperty("modulo") String feature,
        @JsonProperty("funcao") String function,
        @JsonProperty("quantidade") long count,
        @JsonProperty("tokensEntrada") long inputTokens,
        @JsonProperty("tokensSaida") long outputTokens,
        @JsonProperty("arquivos") long files,
        @JsonProperty("paginas") long pages,
        @JsonProperty("custoEstimadoUsd")
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = AbsentFilter.class)
        String estimatedCostUsd) {
}
