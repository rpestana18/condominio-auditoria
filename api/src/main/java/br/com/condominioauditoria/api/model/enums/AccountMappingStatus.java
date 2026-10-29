package br.com.condominioauditoria.api.model.enums;

/**
 * Status of an account's mapping (Q54, 29/10/2026). AUTOMATIC (linked by the system) and CONFIRMED (by the Admin) enter
 * the numbers; REJECTED is a link the Admin refused (the account goes to "sem linha da PO", with the warning, until the
 * Admin chooses a line or the target "sem linha da PO"). SUGGESTED is the old status (before Q54), kept only for the trail; nothing writes it any more.
 */
public enum AccountMappingStatus {
    AUTOMATIC("automático"), SUGGESTED("sugerido"), CONFIRMED("confirmado"), REJECTED("recusado");

    private final String label;

    AccountMappingStatus(String label) {
        this.label = label;
    }

    /** Lowercase Portuguese word shown on screen and in the trail. */
    public String label() {
        return label;
    }

    /** True when the account's target counts in the numbers (Q54): automatic or confirmed. */
    public boolean effective() {
        return this == AUTOMATIC || this == CONFIRMED;
    }
}
