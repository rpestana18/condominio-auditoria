package br.com.condominioauditoria.api.dto.request.budget;

import java.util.UUID;

/** Entry (id of the "a realocar" evidence) and the target budget line. */
public record ReallocationRequest(UUID entryId, UUID lineId) {
}
