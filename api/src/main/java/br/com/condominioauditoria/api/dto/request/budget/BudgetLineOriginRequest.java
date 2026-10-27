package br.com.condominioauditoria.api.dto.request.budget;

import br.com.condominioauditoria.api.model.enums.OriginMethod;
import java.math.BigDecimal;
import java.util.List;

/**
 * Origin of a line filled in or corrected by the Admin (RF-13.13). Replaces every field; the source becomes ADMIN.
 * Months as YYYY-MM; {@code indexRate} as a decimal fraction (0.0723 = 7,23%); money with 2 decimals.
 */
public record BudgetLineOriginRequest(
        List<String> sourceAccounts,
        String averageFrom,
        String averageTo,
        BigDecimal averageAmount,
        BigDecimal baseAmount,
        OriginMethod method,
        String indexName,
        BigDecimal indexRate,
        Boolean indexForecast,
        String indexNote,
        BigDecimal finalAmount,
        String reason) {
}
