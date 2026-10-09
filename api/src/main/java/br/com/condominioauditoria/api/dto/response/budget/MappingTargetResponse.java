package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.MappingTargetType;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** Target of an account mapping, with the text shown on screen. */
public record MappingTargetResponse(
        @JsonProperty("tipo") MappingTargetType type,
        @JsonProperty("linhaId") UUID lineId,
        @JsonProperty("codigo") String code,
        @JsonProperty("descricao") String description,
        @JsonProperty("detalhe") String detail,
        @JsonProperty("texto") String text) {
}
