package br.com.condominioauditoria.api.model.enums;

/**
 * How the amount of a budget line was reached (RF-13.13; ADR 0009, Decision 3 B), deduced from the numbers of the
 * support sheet with a tolerance of R$ 0,01.
 */
public enum OriginMethod {
    AVERAGE_TIMES_INDEX("média × índice"),
    EQUAL_TO_AVERAGE("igual à média"),
    TYPED("valor digitado"),
    /** The base of the line is not the average of any account (fixed amount, zero, separate apportionment). */
    NO_AVERAGE("sem média de conta");

    private final String label;

    OriginMethod(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
