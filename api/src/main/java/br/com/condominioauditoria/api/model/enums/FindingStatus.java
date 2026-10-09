package br.com.condominioauditoria.api.model.enums;

/**
 * Finding statuses. ABERTO and NAO_SE_APLICA_MAIS belong to the system: recalculation opens, closes (the condition no
 * longer exists, Q27) and reopens. JUSTIFICADO, RESOLVIDO and FALSO_POSITIVO are human markings of RF-02.8, which come
 * with the findings screen; a finding marked by a person never changes status through recalculation.
 */
public enum FindingStatus {
    ABERTO, NAO_SE_APLICA_MAIS, JUSTIFICADO, RESOLVIDO, FALSO_POSITIVO;

    /** Status set by the system (recalculation may change it). */
    public boolean isSystemSet() {
        return this == ABERTO || this == NAO_SE_APLICA_MAIS;
    }
}
