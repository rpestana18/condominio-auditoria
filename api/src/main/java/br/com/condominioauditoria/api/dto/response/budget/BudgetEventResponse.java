package br.com.condominioauditoria.api.dto.response.budget;


/** Budget audit trail: confirmation, supersession and fund link changes. */
public record BudgetEventResponse(
        String type,
        String username,
        java.time.Instant at,
        String justification,
        String detail) {
}
