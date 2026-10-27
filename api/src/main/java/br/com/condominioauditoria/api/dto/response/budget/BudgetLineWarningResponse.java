package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.BudgetLineWarningCode;

/** Warning of the line review (RF-13.12): what deserves attention, never a finding and never a block. */
public record BudgetLineWarningResponse(BudgetLineWarningCode code, String message) {
}
