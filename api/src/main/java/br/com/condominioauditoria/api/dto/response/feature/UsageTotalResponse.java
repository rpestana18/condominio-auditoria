package br.com.condominioauditoria.api.dto.response.feature;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Usage row. estimatedCostUsd: absent without tokens or with the cost unavailable; null with a model without price;
 * otherwise decimal text with 2 places ("3.50"), formatted from the BigDecimal.
 */
public record UsageTotalResponse(
        @JsonInclude(JsonInclude.Include.NON_NULL) String month,
        String feature,
        String function,
        long count,
        long inputTokens,
        long outputTokens,
        long files,
        long pages,
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = AbsentFilter.class)
        String estimatedCostUsd) {
}
