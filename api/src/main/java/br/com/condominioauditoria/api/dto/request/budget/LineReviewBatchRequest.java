package br.com.condominioauditoria.api.dto.request.budget;

import java.util.List;

/**
 * Review of the budget lines by the Admin (RF-13.12). {@code reviewAll}: "conferir todas", every line not yet reviewed
 * becomes REVIEWED (the items, if any, are applied first). {@code reason}: optional, goes to the trail.
 */
public record LineReviewBatchRequest(
        List<LineReviewItemRequest> items,
        boolean reviewAll,
        String reason) {
}
