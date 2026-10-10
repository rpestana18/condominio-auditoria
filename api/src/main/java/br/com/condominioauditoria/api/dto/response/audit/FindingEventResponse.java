package br.com.condominioauditoria.api.dto.response.audit;

import br.com.condominioauditoria.api.model.enums.FindingStatus;
import java.time.Instant;

public record FindingEventResponse(
        FindingStatus previousStatus,
        FindingStatus newStatus,
        boolean conditionPresent,
        String reason,
        String username,
        Instant occurredAt) {
}
