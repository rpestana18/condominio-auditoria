package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** A line that shares a repeated printed code. */
public record RepeatedLineResponse(
        @JsonProperty("linhaId") UUID lineId,
        @JsonProperty("ordem") int position,
        @JsonProperty("descricao") String description,
        @JsonProperty("codigoEfetivo") String effectiveCode) {
}
