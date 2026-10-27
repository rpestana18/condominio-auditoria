package br.com.condominioauditoria.api.dto.response.budget;

import java.util.UUID;

/**
 * Review of the budget lines (RF-13.12; ADR 0009, Decision 1), in every response that distributes the expense by line.
 * {@code reviewed} false = the pending step "conferir as linhas da PO": the numbers by line are not shown (no 4xx, as
 * the pending account mapping of PR #58). {@code totalLines}: expense lines (groups 1.1 to 1.8) and fund lines (1.9).
 */
public record LineReviewStateResponse(
        UUID budgetId,
        boolean reviewed,
        int reviewedLines,
        int totalLines,
        String message) {
}
