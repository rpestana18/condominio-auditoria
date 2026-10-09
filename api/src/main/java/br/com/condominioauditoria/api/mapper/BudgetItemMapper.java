package br.com.condominioauditoria.api.mapper;

import br.com.condominioauditoria.api.dto.response.budget.BudgetItemEventResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetItemResponse;
import br.com.condominioauditoria.api.model.budget.BudgetItem;
import br.com.condominioauditoria.api.model.budget.BudgetItemEvent;

/** Budget item (rubrica) entities → budget item API DTOs (contracts/openapi.yaml). */
public final class BudgetItemMapper {

    private BudgetItemMapper() {
    }

    public static BudgetItemResponse toResponse(BudgetItem item) {
        return new BudgetItemResponse(item.getId(), item.getName(), item.getGroupCode(), item.getSourceLineId(),
                item.getCreatedBy(), item.getCreatedAt());
    }

    public static BudgetItemEventResponse toResponse(BudgetItemEvent event) {
        return new BudgetItemEventResponse(event.getId(), event.getBudgetLineId(), event.getLineCode(),
                event.getLineDescription(), event.getAction(), event.getUsername(), event.getOccurredAt(),
                event.getPreviousItem(), event.getPreviousStatus(), event.getNewItem(), event.getNewStatus(),
                event.getSource(), event.getReason());
    }
}
