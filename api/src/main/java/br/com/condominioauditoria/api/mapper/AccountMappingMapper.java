package br.com.condominioauditoria.api.mapper;

import br.com.condominioauditoria.api.dto.response.budget.AccountMappingEventResponse;
import br.com.condominioauditoria.api.dto.response.budget.MappingTargetResponse;
import br.com.condominioauditoria.api.model.budget.AccountMappingEvent;
import br.com.condominioauditoria.api.model.budget.MappingTarget;

/** Account mapping (de-para) entities and values → account mapping API DTOs (contracts/openapi.yaml). */
public final class AccountMappingMapper {

    private AccountMappingMapper() {
    }

    /** Null when the account has no target yet. */
    public static MappingTargetResponse toResponse(MappingTarget target) {
        return target == null ? null : new MappingTargetResponse(target.type(), target.budgetLineId(), target.code(),
                target.description(), target.detail(), target.text());
    }

    public static AccountMappingEventResponse toResponse(AccountMappingEvent event) {
        return new AccountMappingEventResponse(event.getId(), event.getAccountCode(), event.getAccountName(),
                event.getAction(), event.getUsername(), event.getOccurredAt(), event.getPreviousTarget(),
                event.getPreviousStatus(), event.getNewTarget(), event.getNewStatus(), event.getSource(),
                event.getReason());
    }
}
