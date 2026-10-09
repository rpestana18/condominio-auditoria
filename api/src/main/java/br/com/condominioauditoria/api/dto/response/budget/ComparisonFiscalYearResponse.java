package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code budgetId}: the budget whose evidence the click opens in budget vs. actual; null for the printed column, which
 * has no actual.
 */
public record ComparisonFiscalYearResponse(
        String id,
        @JsonProperty("rotulo") String label,
        @JsonProperty("execucao") BigDecimal execution,
        @JsonProperty("periodo") String period,
        @JsonProperty("poId") UUID budgetId) {
}
