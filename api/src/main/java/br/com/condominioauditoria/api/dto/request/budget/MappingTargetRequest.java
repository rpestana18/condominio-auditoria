package br.com.condominioauditoria.api.dto.request.budget;

import br.com.condominioauditoria.api.model.enums.MappingTargetType;
import java.util.UUID;

/** Target chosen by the Admin. Null {@code confirm} = true (choosing from the list already confirms). */
public record MappingTargetRequest(
        MappingTargetType type,
        UUID lineId,
        String detail,
        Boolean confirm) {
}
