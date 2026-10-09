package br.com.condominioauditoria.api.dto.request.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** Entry (id of the "a realocar" evidence) and the target budget line. */
public record ReallocationRequest(@JsonProperty("lancamentoId") UUID entryId, @JsonProperty("linhaId") UUID lineId) {
}
