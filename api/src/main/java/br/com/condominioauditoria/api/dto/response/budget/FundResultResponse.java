package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.FundComparisonStatus;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Reserve, works and other funds. {@code planned}, {@code collected}, {@code difference} and {@code execution} only
 * with COMPARED; {@code credits} and {@code debits} are the movement of the period.
 */
public record FundResultResponse(
        UUID fundId,
        String fund,
        UUID lineId,
        String lineCode,
        FundComparisonStatus status,
        BigDecimal planned,
        BigDecimal collected,
        BigDecimal difference,
        BigDecimal execution,
        BigDecimal credits,
        BigDecimal debits) {
}
