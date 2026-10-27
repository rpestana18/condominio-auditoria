package br.com.condominioauditoria.api.dto.request.budget;

import br.com.condominioauditoria.api.model.enums.BudgetLineReviewStatus;
import java.util.UUID;

/**
 * One line of the review. {@code status} null keeps the current state; {@code groupAccount} and
 * {@code descriptionPrevails} null keep the current marks.
 */
public record LineReviewItemRequest(
        UUID lineId,
        BudgetLineReviewStatus status,
        Boolean groupAccount,
        Boolean descriptionPrevails) {
}
