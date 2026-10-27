package br.com.condominioauditoria.api.model.enums;

/** Review of a budget line by a person (RF-13.12; ADR 0009, Decision 1). */
public enum BudgetLineReviewStatus {
    PENDING("não conferida"), REVIEWED("conferida");

    private final String label;

    BudgetLineReviewStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
