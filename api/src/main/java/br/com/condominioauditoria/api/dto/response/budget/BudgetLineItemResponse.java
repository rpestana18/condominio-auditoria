package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.BudgetItemSource;
import br.com.condominioauditoria.api.model.enums.BudgetItemStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A budget line and its budget item. Null {@code status}: line without item. {@code account}: what the suggestion
 * compares (the budget's account, the account column text or, without both, the description).
 */
public record BudgetLineItemResponse(
        UUID lineId,
        String code,
        String group,
        String account,
        String description,
        BigDecimal budgeted,
        BudgetItemResponse budgetItem,
        BudgetItemStatus status,
        BudgetItemSource source,
        String reason,
        String updatedBy,
        Instant updatedAt) {
}
