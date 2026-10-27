package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.BudgetStatus;
import java.util.List;
import java.util.UUID;

/**
 * Review of the budget lines (RF-13.12; ADR 0009, Decision 1): every expense line and every 1.9 fund line, in budget
 * order. {@code state}: the same summary that the screens which distribute spending by line receive.
 * {@code linesWithOrigin}: lines with an origin (support sheet or Admin, RF-13.13).
 */
public record BudgetLineReviewResponse(
        UUID budgetId,
        Integer version,
        BudgetStatus budgetStatus,
        String checkVersion,
        LineReviewStateResponse state,
        int linesWithWarnings,
        int linesWithOrigin,
        List<LineReviewLineResponse> lines) {
}
