package br.com.condominioauditoria.api.model.enums;

/** Mark read in the budget's account column (RF-03.1.1). A line with a mark has no account. */
public enum BudgetLineMark {
    SEPARATE_APPORTIONMENT, NEGOTIATED_EXEMPTION, NO_AMOUNT, FIXED_AMOUNT_NO_REFERENCE
}
