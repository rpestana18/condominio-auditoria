package br.com.condominioauditoria.api.dto.response.budget;

import java.util.UUID;

/** Line skipped in a batch, with the reason. */
public record SkippedLineResponse(UUID lineId, String reason) {
}
