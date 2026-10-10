package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.AccountMappingAction;
import br.com.condominioauditoria.api.model.enums.AccountMappingSource;
import br.com.condominioauditoria.api.model.enums.AccountMappingStatus;
import java.time.Instant;
import java.util.UUID;

/** An entry of the account mapping trail, with the targets as text. */
public record AccountMappingEventResponse(
        UUID id,
        String account,
        String name,
        AccountMappingAction action,
        String username,
        Instant at,
        String previousTarget,
        AccountMappingStatus previousStatus,
        String newTarget,
        AccountMappingStatus newStatus,
        AccountMappingSource source,
        String reason) {
}
