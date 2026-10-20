package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.SpendingProfile;
import br.com.condominioauditoria.api.model.enums.SpendingProfileStatus;
import java.time.Instant;
import java.util.UUID;

/** Event of the spending profile trail. */
public record SpendingProfileEventResponse(
        UUID id,
        UUID lineId,
        String lineCode,
        String lineDescription,
        SpendingProfileStatus action,
        String username,
        Instant occurredAt,
        SpendingProfile previousProfile,
        SpendingProfileStatus previousStatus,
        SpendingProfile newProfile,
        SpendingProfileStatus newStatus,
        String reason) {
}
