package br.com.condominioauditoria.api.model.enums;

/**
 * Target of a cash flow account in the mapping (RF-03.1.4, Terms): a budget line; "Ajuste (não é despesa)"; "Meio de
 * pagamento (a realocar, RF-02B)"; or "Transferência entre fundos".
 */
public enum MappingTargetType {
    BUDGET_LINE, ADJUSTMENT, TO_REALLOCATE, TRANSFER
}
