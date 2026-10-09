package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Budget extended by the Admin (RF-11.3): valid from {@code from} until {@code until} (YYYY-MM), after the fiscal year.
 * Null without an extension.
 */
public record BudgetExtensionResponse(
        @JsonProperty("de") String from,
        @JsonProperty("ate") String until,
        @JsonProperty("justificativa") String justification,
        @JsonProperty("por") String by,
        @JsonProperty("em") Instant at) {
}
