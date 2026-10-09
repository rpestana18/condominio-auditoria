package br.com.condominioauditoria.api.dto.response.feature;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/** A period in which a feature was enabled (RF-10.6). Null start = enabled by default; null end = still enabled. */
public record ActivePeriodResponse(
        @JsonProperty("modulo") String feature,
        @JsonProperty("inicio") Instant start,
        @JsonProperty("fim") Instant end,
        @JsonProperty("ligadoPor") String enabledBy,
        @JsonProperty("motivoLigar") String enableReason,
        @JsonProperty("desligadoPor") String disabledBy,
        @JsonProperty("motivoDesligar") String disableReason) {
}
