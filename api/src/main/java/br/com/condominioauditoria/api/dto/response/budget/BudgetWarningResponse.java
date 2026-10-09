package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.BudgetWarningCode;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Informational budget warning (never a finding). */
public record BudgetWarningResponse(
        @JsonProperty("codigo") BudgetWarningCode code,
        @JsonProperty("texto") String text) {
}
