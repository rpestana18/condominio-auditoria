package br.com.condominioauditoria.api.dto.response.budget;

import java.util.UUID;

/** A line that shares a repeated printed code. */
public record RepeatedLineResponse(
        UUID lineId,
        int position,
        String description,
        String effectiveCode) {
}
