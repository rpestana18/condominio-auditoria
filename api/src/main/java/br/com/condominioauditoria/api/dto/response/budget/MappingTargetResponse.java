package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.MappingTargetType;
import java.util.UUID;

/** Target of an account mapping, with the text shown on screen. */
public record MappingTargetResponse(
        MappingTargetType type,
        UUID lineId,
        String code,
        String description,
        String detail,
        String text) {
}
