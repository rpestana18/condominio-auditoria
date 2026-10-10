package br.com.condominioauditoria.api.model.enums;

/** Status of a budget line's item (RF-11.7): only CONFIRMED enters the comparison by line. */
public enum BudgetItemStatus {
    SUGGESTED("sugerido"), CONFIRMED("confirmado"), REJECTED("recusado");

    private final String label;

    BudgetItemStatus(String label) {
        this.label = label;
    }

    /** Lowercase Portuguese word shown on screen and in the trail. */
    public String label() {
        return label;
    }
}
