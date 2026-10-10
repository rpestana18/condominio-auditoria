package br.com.condominioauditoria.api.model.enums;

/** Status of an account's mapping. Only CONFIRMED enters budget vs. actual; a suggestion never confirms itself. */
public enum AccountMappingStatus {
    SUGGESTED("sugerido"), CONFIRMED("confirmado"), REJECTED("recusado");

    private final String label;

    AccountMappingStatus(String label) {
        this.label = label;
    }

    /** Lowercase Portuguese word shown on screen and in the trail. */
    public String label() {
        return label;
    }
}
