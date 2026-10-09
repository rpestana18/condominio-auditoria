package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** Line that makes up a value in the comparison, with its evidence target. */
public record UsedLineResponse(
        @JsonProperty("linhaId") UUID lineId,
        @JsonProperty("codigo") String code,
        @JsonProperty("descricao") String description,
        @JsonProperty("alvo") String target) {
}
