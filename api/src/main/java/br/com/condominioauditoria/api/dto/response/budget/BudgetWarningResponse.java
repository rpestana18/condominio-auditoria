package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.BudgetWarningCode;

/** Informational budget warning (never a finding). */
public record BudgetWarningResponse(
        BudgetWarningCode code,
        String text) {
}
