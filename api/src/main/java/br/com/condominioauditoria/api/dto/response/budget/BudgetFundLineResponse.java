package br.com.condominioauditoria.api.dto.response.budget;

import java.math.BigDecimal;
import java.util.UUID;

/** Fund line (1.9.x) of the budget and the cash flow fund it is linked to. */
public record BudgetFundLineResponse(
        UUID lineId,
        String effectiveCode,
        String description,
        BigDecimal budgeted,
        UUID fundId,
        String fund) {
}
