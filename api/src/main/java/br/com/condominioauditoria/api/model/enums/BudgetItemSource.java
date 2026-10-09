package br.com.condominioauditoria.api.model.enums;

/**
 * Where the line's item came from (ADR 0005, Decision 1): the condominium's first confirmed budget (the line becomes an
 * item), the same budget account in the same group, the same line of the previous version of the same fiscal year or
 * the Admin's choice.
 */
public enum BudgetItemSource {
    PRIMEIRA_PO, CONTA_PO, VERSAO_ANTERIOR, MANUAL
}
