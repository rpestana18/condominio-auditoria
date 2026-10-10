package br.com.condominioauditoria.api.model.enums;

/**
 * Filters of the "De-para" screen (RF-03.1.13). PENDING = without confirmed mapping (none, suggested or rejected).
 */
public enum AccountMappingFilter {
    ALL, PENDING, SUGGESTED, CONFIRMED, REJECTED, UNMAPPED, SAME_AS_PREVIOUS_VERSION
}
