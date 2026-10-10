package br.com.condominioauditoria.api.dto.response.budget;

import java.util.UUID;

/** Budget line left without an item suggestion, with the reason. */
public record LineWithoutSuggestionResponse(
        UUID lineId,
        String code,
        String account,
        String reason) {
}
