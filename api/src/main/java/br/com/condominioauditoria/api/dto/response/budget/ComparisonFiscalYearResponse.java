package br.com.condominioauditoria.api.dto.response.budget;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code budgetId}: the budget whose evidence the click opens in budget vs. actual; null for the printed column, which
 * has no actual.
 */
public record ComparisonFiscalYearResponse(
        String id,
        String label,
        BigDecimal execution,
        String period,
        UUID budgetId) {
}
