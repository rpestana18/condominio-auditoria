package br.com.condominioauditoria.api.dto.response.budget;

import java.util.UUID;

/**
 * Review of the budget lines (RF-13.12; ADR 0009, Decision 1, revised by Q54 on 29/10/2026), in every response that
 * distributes the expense by line. Since Q54 the lines read are valid automatically: {@code reviewed} is always true,
 * {@code message} is null and no screen waits for a review. {@code reviewedLines}: lines a person confirmed (optional);
 * {@code totalLines}: expense lines (groups 1.1 to 1.8) and fund lines (1.9).
 */
public record LineReviewStateResponse(
        UUID budgetId,
        boolean reviewed,
        int reviewedLines,
        int totalLines,
        String message) {
}
