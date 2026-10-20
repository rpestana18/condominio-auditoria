package br.com.condominioauditoria.api.model.enums;

/** State of a cash flow account on the utility accounts screen (RF-12.1). */
public enum UtilityAccountStatus {
    /** The system suggests a utility by the account name; it does not enter the charts yet. */
    SUGGESTED,
    /** The Manager or the Admin confirmed the utility. */
    CONFIRMED,
    /** The Manager or the Admin said it is not a utility account. */
    REJECTED,
    /** No suggestion and no decision. */
    NONE
}
