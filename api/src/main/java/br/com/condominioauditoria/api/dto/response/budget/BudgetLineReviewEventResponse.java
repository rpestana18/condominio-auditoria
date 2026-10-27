package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.BudgetLineReviewAction;
import br.com.condominioauditoria.api.model.enums.BudgetLineReviewStatus;
import java.time.Instant;
import java.util.UUID;

/** Event of the line review trail (insert only): who, when, the state before and after, the marks and the reason. */
public record BudgetLineReviewEventResponse(
        UUID id,
        UUID lineId,
        String lineCode,
        String lineDescription,
        BudgetLineReviewAction action,
        String username,
        Instant occurredAt,
        BudgetLineReviewStatus previousStatus,
        BudgetLineReviewStatus newStatus,
        boolean groupAccount,
        boolean descriptionPrevails,
        String reason) {
}
