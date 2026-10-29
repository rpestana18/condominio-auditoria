package br.com.condominioauditoria.api.model.enums;

/**
 * Review of a budget line (RF-13.12; ADR 0009, Decision 1, revised by Q54): PENDING = "automática", the line read from
 * the document, valid without a review (the name stays in the database and in the trail); REVIEWED = a person confirmed
 * it (optional).
 */
public enum BudgetLineReviewStatus {
    PENDING("automática"), REVIEWED("conferida");

    private final String label;

    BudgetLineReviewStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
