package br.com.condominioauditoria.api.model.enums;

/**
 * Where the target came from (ADR 0004, Decision 4): copy of the budget's previous version, sheet uploaded by the
 * Admin, name comparison (without AI) or the Admin's choice in the line list.
 */
public enum AccountMappingSource {
    VERSAO_ANTERIOR, PLANILHA, NOME, ADMIN
}
