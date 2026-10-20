package br.com.condominioauditoria.api.model.enums;

/** Status of a line's spending profile (RF-13.3): only CONFIRMED is used; SUGGESTED is shown as "tipo não confirmado". */
public enum SpendingProfileStatus {
    SUGGESTED("sugerido"), CONFIRMED("confirmado");

    private final String label;

    SpendingProfileStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
