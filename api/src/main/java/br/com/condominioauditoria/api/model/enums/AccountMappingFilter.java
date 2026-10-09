package br.com.condominioauditoria.api.model.enums;

/**
 * Filters of the "De-para" screen (RF-03.1.13). PENDENTES = without confirmed mapping (none, suggested or rejected).
 */
public enum AccountMappingFilter {
    TODAS, PENDENTES, SUGERIDO, CONFIRMADO, RECUSADO, SEM_DEPARA, IGUAIS_VERSAO_ANTERIOR
}
