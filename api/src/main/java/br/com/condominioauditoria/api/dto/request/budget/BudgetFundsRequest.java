package br.com.condominioauditoria.api.dto.request.budget;

import java.util.List;

/** Complete link of the budget's fund lines. */
public record BudgetFundsRequest(List<FundLinkRequest> funds) {
}
