package br.com.condominioauditoria.api.model.enums;

/** Budget lifecycle (ADR 0004, Decision 3). */
public enum BudgetStatus {
    /** Read and checked; waiting for the Admin's confirmation. */
    LIDA,
    /** Some sum check did not match beyond the rounding tolerance (RF-03.1.2). */
    LIDA_COM_DIVERGENCIA,
    /** Confirmed by the Admin; valid for the months of the fiscal year. No longer changes on reprocessing. */
    CONFIRMADA,
    /** Replaced by a reapproval from a given month; still valid for the earlier months. */
    SUBSTITUIDA;

    /** A confirmed (or superseded) budget does not accept a new reading: its numbers may already have been exported. */
    public boolean isLocked() {
        return this == CONFIRMADA || this == SUBSTITUIDA;
    }
}
