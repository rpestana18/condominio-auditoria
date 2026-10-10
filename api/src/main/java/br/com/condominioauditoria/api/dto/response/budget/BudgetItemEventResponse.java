package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.BudgetItemAction;
import br.com.condominioauditoria.api.model.enums.BudgetItemSource;
import br.com.condominioauditoria.api.model.enums.BudgetItemStatus;
import java.time.Instant;
import java.util.UUID;

/** An entry of the budget item trail, with the items by name. */
public record BudgetItemEventResponse(
        UUID id,
        UUID lineId,
        String code,
        String description,
        BudgetItemAction action,
        String username,
        Instant at,
        String previousItem,
        BudgetItemStatus previousStatus,
        String newItem,
        BudgetItemStatus newStatus,
        BudgetItemSource source,
        String reason) {
}
