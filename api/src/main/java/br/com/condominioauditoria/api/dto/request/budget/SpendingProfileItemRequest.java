package br.com.condominioauditoria.api.dto.request.budget;

import br.com.condominioauditoria.api.model.enums.SpendingProfile;
import java.util.UUID;

/** Profile of one line; null {@code profile} = confirm the current suggestion. */
public record SpendingProfileItemRequest(
        UUID lineId,
        SpendingProfile profile) {
}
