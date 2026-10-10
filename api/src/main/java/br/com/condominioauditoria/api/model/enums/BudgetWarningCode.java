package br.com.condominioauditoria.api.model.enums;

/** Code of an informational budget warning; the names are part of the API contract. */
public enum BudgetWarningCode {
    ROUNDING, DISCREPANCY, REPEATED_CODE, CONFIRMED_WITH_DISCREPANCY, OUTSIDE_FIRST_QUARTER, NO_MINUTES,
    RULE_NOT_EVALUATED
}
