package br.com.condominioauditoria.api.model.enums;

/**
 * How a budget line spends over the fiscal year (RF-13.14; ADR 0009, Decision 2, which revised ADR 0007, Decision 2).
 * Never changes the planned amount (the same in every month, Q24): only the bridge item where the line's balance goes
 * (RF-13.15) and what the tracking grid highlights (RF-13.4). Without confirmation, the current suggestion holds,
 * shown as "tipo não confirmado".
 */
public enum SpendingProfile {
    /** Paid every month (staff, service contracts, phone, internet). The old MONTHLY. Compared month by month. */
    RECURRING("recorrente"),
    /** One-off purchase or service: not spending it is not savings (D4); each purchase is compared with the line. */
    OCCASIONAL("eventual"),
    /** Concentrated in part of the year (summer reinforcement, O5): compared over the fiscal year, not the month. */
    SEASONAL("sazonal"),
    /** Paid once a year (O6): compared over the fiscal year. */
    ANNUAL("anual"),
    /** Holidays, termination, 13th salary: the balance is kept for a payment to come, never savings (Q44). */
    PROVISION("provisão"),
    /** Electricity, water, gas, building insurance: zero budget, outside the PO (Q45, N3). */
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
