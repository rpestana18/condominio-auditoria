package br.com.condominioauditoria.api.dto.response.feature;

import java.time.Instant;

/** A period in which a feature was enabled (RF-10.6). Null start = enabled by default; null end = still enabled. */
public record ActivePeriodResponse(
        String feature,
        Instant start,
        Instant end,
        String enabledBy,
        String enableReason,
        String disabledBy,
        String disableReason) {
}
