package br.com.condominioauditoria.api.model.enums;

/**
 * Event of the budget line review trail (RF-13.12, RF-13.13): reviewed, back to pending, the Admin's marks changed, or
 * the origin of the line corrected by the Admin.
 */
public enum BudgetLineReviewAction {
    REVIEWED, REOPENED, MARKS_CHANGED, ORIGIN_CHANGED
}
