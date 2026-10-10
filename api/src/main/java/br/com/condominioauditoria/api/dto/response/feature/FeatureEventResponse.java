package br.com.condominioauditoria.api.dto.response.feature;

import java.time.Instant;
import java.util.UUID;

/** One enable or disable in the activation trail (RF-10.6). */
public record FeatureEventResponse(
        UUID id,
        String feature,
        boolean enabledBefore,
        boolean enabledAfter,
        String username,
        Instant occurredAt,
        String reason) {
}
