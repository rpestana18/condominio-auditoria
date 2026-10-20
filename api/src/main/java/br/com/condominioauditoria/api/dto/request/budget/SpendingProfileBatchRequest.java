package br.com.condominioauditoria.api.dto.request.budget;

import java.util.List;

/** Profiles confirmed by the Admin (RF-13.3), with an optional reason. */
public record SpendingProfileBatchRequest(
        List<SpendingProfileItemRequest> items,
        String reason) {
}
