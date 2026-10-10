package br.com.condominioauditoria.api.dto.response.budget;

import java.time.Instant;

/**
 * Budget extended by the Admin (RF-11.3): valid from {@code from} until {@code until} (YYYY-MM), after the fiscal year.
 * Null without an extension.
 */
public record BudgetExtensionResponse(
        String from,
        String until,
        String justification,
        String by,
        Instant at) {
}
