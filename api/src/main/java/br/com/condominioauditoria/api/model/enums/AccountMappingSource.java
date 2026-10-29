package br.com.condominioauditoria.api.model.enums;

/**
 * Where the target came from (ADR 0004, Decision 4; ADR 0009, Decision 8): copy of the budget's previous version, sheet
 * uploaded by the Admin, the budget's support sheet (expense accounts and line origins, Q54), name comparison (without
 * AI) or the Admin's choice in the line list.
 */
public enum AccountMappingSource {
    PREVIOUS_VERSION, SPREADSHEET, BUDGET_SHEET, NAME, ADMIN
}
