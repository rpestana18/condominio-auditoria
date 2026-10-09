package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** Line skipped in a batch, with the reason. */
public record SkippedLineResponse(@JsonProperty("linhaId") UUID lineId, @JsonProperty("motivo") String reason) {
}
