package br.com.condominioauditoria.api.model.enums;

/**
 * Target of a cash flow account in the mapping (RF-03.1.4, Terms): a budget line; "Ajuste (não é despesa)"; "Meio de
 * pagamento (a realocar, RF-02B)"; "Transferência entre fundos"; or "Sem linha da PO", the Admin's decision that the
 * account has no line (Q54, O7: it stays in "sem linha da PO" without the warning of an account without a link).
 */
public enum MappingTargetType {
    BUDGET_LINE, ADJUSTMENT, TO_REALLOCATE, TRANSFER, WITHOUT_LINE
}
