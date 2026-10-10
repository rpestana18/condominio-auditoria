package br.com.condominioauditoria.api.model.enums;

/** Whether budget vs. actual has numbers; any value other than CALCULATED says why not (RF-03.1.10). */
public enum BudgetVsActualStatus {
    CALCULATED, NO_BUDGET, BUDGET_NOT_CONFIRMED, NO_OPERATING_FUND, NO_CASH_FLOW, TWO_CASH_FLOWS
}
