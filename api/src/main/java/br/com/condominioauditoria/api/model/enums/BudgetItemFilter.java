package br.com.condominioauditoria.api.model.enums;

/** Filters of a budget's items screen. PENDING = without confirmed item (none, suggested or rejected). */
public enum BudgetItemFilter {
    ALL, PENDING, SUGGESTED, CONFIRMED, REJECTED, NO_ITEM
}
