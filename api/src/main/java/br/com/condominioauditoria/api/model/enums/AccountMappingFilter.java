package br.com.condominioauditoria.api.model.enums;

/**
 * Filters of the "De-para" screen (RF-03.1.13, Q54). EXCEPTIONS ("só exceções") = accounts without a link found and
 * links of low confidence; PENDING = not confirmed by the Admin (none, automatic or rejected); SUGGESTED only finds old
 * rows (before Q54).
 */
public enum AccountMappingFilter {
    ALL, EXCEPTIONS, AUTOMATIC, LOW_CONFIDENCE, PENDING, SUGGESTED, CONFIRMED, REJECTED, UNMAPPED,
    SAME_AS_PREVIOUS_VERSION
}
