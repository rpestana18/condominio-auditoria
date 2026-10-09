package br.com.condominioauditoria.api.model.enums;

/** Status of a budget line's item (RF-11.7): only CONFIRMADO enters the comparison by line. */
public enum BudgetItemStatus {
    SUGERIDO, CONFIRMADO, RECUSADO
}
