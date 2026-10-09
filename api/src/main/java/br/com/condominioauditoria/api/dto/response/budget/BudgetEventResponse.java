package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Budget audit trail: confirmation, supersession and fund link changes. */
public record BudgetEventResponse(
        @JsonProperty("tipo") String type,
        @JsonProperty("usuario") String username,
        @JsonProperty("em") java.time.Instant at,
        @JsonProperty("justificativa") String justification,
        @JsonProperty("detalhe") String detail) {
}
