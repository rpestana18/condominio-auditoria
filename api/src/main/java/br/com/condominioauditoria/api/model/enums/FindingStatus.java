package br.com.condominioauditoria.api.model.enums;

/**
 * Finding statuses. OPEN and NO_LONGER_APPLIES belong to the system: recalculation opens, closes (the condition no
 * longer exists, Q27) and reopens. JUSTIFIED, RESOLVED and FALSE_POSITIVE are human markings of RF-02.8, which come
 * with the findings screen; a finding marked by a person never changes status through recalculation.
 */
public enum FindingStatus {
    OPEN, NO_LONGER_APPLIES, JUSTIFIED, RESOLVED, FALSE_POSITIVE;

    /** Status set by the system (recalculation may change it). */
    public boolean isSystemSet() {
        return this == OPEN || this == NO_LONGER_APPLIES;
    }
}
