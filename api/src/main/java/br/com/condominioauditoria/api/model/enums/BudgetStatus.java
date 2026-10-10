package br.com.condominioauditoria.api.model.enums;

/** Budget lifecycle (ADR 0004, Decision 3). */
public enum BudgetStatus {
    /** Read and checked; waiting for the Admin's confirmation. */
    READ,
    /** Some sum check did not match beyond the rounding tolerance (RF-03.1.2). */
    READ_WITH_DISCREPANCY,
    /** Confirmed by the Admin; valid for the months of the fiscal year. No longer changes on reprocessing. */
    CONFIRMED,
    /** Replaced by a reapproval from a given month; still valid for the earlier months. */
    SUPERSEDED;

    /** A confirmed (or superseded) budget does not accept a new reading: its numbers may already have been exported. */
    public boolean isLocked() {
        return this == CONFIRMED || this == SUPERSEDED;
    }
}
