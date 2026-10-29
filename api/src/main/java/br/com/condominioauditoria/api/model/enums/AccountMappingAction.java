package br.com.condominioauditoria.api.model.enums;

/**
 * Recorded action: automatic link (Q54), confirmation, rejection ("sem linha da PO") or target change. SUGGESTED is the
 * old suggestion (before Q54), kept for the trail.
 */
public enum AccountMappingAction {
    AUTOMATIC, SUGGESTED, CONFIRMED, REJECTED, CHANGED
}
