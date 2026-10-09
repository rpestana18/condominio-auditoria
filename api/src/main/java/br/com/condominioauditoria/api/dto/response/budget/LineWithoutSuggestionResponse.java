package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** Budget line left without an item suggestion, with the reason. */
public record LineWithoutSuggestionResponse(
        @JsonProperty("linhaId") UUID lineId,
        @JsonProperty("codigo") String code,
        @JsonProperty("conta") String account,
        @JsonProperty("motivo") String reason) {
}
