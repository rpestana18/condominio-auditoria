package br.com.condominioauditoria.api.model.enums;

/** How a fund is shown in budget vs. actual (RF-03.1.9). */
public enum FundComparisonStatus {
    /** Linked to a 1.9.x line: collection × planned. */
    COMPARED,
    /** Separate apportionment and other funds without a budget line: movement only, no difference (Q22). */
    NOT_PLANNED_IN_BUDGET,
    /** 1.9.x line without a linked fund: no numbers (RF-03.1.9). */
    LINE_WITHOUT_FUND,
    /** Cash flow saved before fee receipts existed (v2): reprocess the cash flow. */
    REPROCESS_CASH_FLOW
}
