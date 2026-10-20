package br.com.condominioauditoria.api.model.enums;

/**
 * How a budget line spends over the fiscal year (RF-13.3; ADR 0007, Decision 2). Never changes the planned amount (the
 * same in every month, Q24): only how the line's balance is classified in the balance bridge (RF-13.6) and what the
 * tracking grid highlights (RF-13.4). Without confirmation, {@link #MONTHLY} holds.
 */
public enum SpendingProfile {
    MONTHLY("mensal"), PROVISION("provisão"), ANNUAL("anual ou esporádica"),
    SEPARATE_APPORTIONMENT("rateio à parte");

    private final String label;

    SpendingProfile(String label) {
        this.label = label;
    }

    /** Lowercase Portuguese words shown on screen and in the trail. */
    public String label() {
        return label;
    }
}
