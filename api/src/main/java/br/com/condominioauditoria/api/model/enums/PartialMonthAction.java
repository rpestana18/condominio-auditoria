package br.com.condominioauditoria.api.model.enums;

/**
 * Partial month trail (RF-13.10; ADR 0007, Decision 5 A): marked partial or complete by the Manager or the Admin, back to
 * the automatic rule, or superseded by the complete cash flow of the same month.
 */
public enum PartialMonthAction {
    MARKED_PARTIAL, MARKED_COMPLETE, AUTOMATIC, SUPERSEDED
}
