package br.com.condominioauditoria.api.model.enums;

/** Status of an account's mapping. Only CONFIRMADO enters budget vs. actual; a suggestion never confirms itself. */
public enum AccountMappingStatus {
    SUGERIDO, CONFIRMADO, RECUSADO
}
