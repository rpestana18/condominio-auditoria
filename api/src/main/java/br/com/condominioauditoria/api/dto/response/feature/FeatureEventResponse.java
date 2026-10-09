package br.com.condominioauditoria.api.dto.response.feature;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/** One enable or disable in the activation trail (RF-10.6). */
public record FeatureEventResponse(
        UUID id,
        @JsonProperty("modulo") String feature,
        @JsonProperty("ligadoAntes") boolean enabledBefore,
        @JsonProperty("ligadoDepois") boolean enabledAfter,
        @JsonProperty("usuario") String username,
        @JsonProperty("quando") Instant occurredAt,
        @JsonProperty("motivo") String reason) {
}
