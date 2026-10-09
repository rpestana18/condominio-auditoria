package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Warning shown with the result (code and text). */
public record BudgetVsActualWarningResponse(@JsonProperty("codigo") String code, @JsonProperty("texto") String text) {
}
