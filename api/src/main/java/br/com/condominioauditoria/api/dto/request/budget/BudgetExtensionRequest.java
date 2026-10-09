package br.com.condominioauditoria.api.dto.request.budget;

import com.fasterxml.jackson.annotation.JsonProperty;

/** The Admin marks the budget as extended until {@code until} (YYYY-MM), with a required justification (RF-11.3). */
public record BudgetExtensionRequest(
        @JsonProperty("ate") String until,
        @JsonProperty("justificativa") String justification) {
}
