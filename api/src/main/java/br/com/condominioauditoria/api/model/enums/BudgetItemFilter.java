package br.com.condominioauditoria.api.model.enums;

/** Filters of a budget's items screen. PENDENTES = without confirmed item (none, suggested or rejected). */
public enum BudgetItemFilter {
    TODAS, PENDENTES, SUGERIDO, CONFIRMADO, RECUSADO, SEM_RUBRICA
}
