package br.com.condominioauditoria.api.dto.request.budget;


/** The Admin marks the budget as extended until {@code until} (YYYY-MM), with a required justification (RF-11.3). */
public record BudgetExtensionRequest(
        String until,
        String justification) {
}
