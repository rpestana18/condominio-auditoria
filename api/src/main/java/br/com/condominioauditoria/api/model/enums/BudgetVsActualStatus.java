package br.com.condominioauditoria.api.model.enums;

/** Whether budget vs. actual has numbers; any value other than CALCULADO says why not (RF-03.1.10). */
public enum BudgetVsActualStatus {
    CALCULADO, SEM_PO, PO_NAO_CONFIRMADA, SEM_FUNDO_ORDINARIO, SEM_FLUXO, DOIS_FLUXOS
}
