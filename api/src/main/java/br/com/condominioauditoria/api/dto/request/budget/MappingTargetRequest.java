package br.com.condominioauditoria.api.dto.request.budget;

import br.com.condominioauditoria.api.model.enums.MappingTargetType;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** Target chosen by the Admin. Null {@code confirm} = true (choosing from the list already confirms). */
public record MappingTargetRequest(
        @JsonProperty("tipo") MappingTargetType type,
        @JsonProperty("linhaId") UUID lineId,
        @JsonProperty("detalhe") String detail,
        @JsonProperty("confirmar") Boolean confirm) {
}
