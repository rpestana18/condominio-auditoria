package br.com.condominioauditoria.api.model.enums;

/** Utility billed by consumption (RF-12): water and sewage, electricity and gas. */
public enum UtilityType {
    WATER("Água e esgoto"),
    ELECTRICITY("Energia"),
    GAS("Gás");

    private final String label;

    UtilityType(String label) {
        this.label = label;
    }

    /** Name shown on the screen. */
    public String label() {
        return label;
    }
}
