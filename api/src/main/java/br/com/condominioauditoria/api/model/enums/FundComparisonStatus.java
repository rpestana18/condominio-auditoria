package br.com.condominioauditoria.api.model.enums;

/** How a fund is shown in budget vs. actual (RF-03.1.9). */
public enum FundComparisonStatus {
    /** Linked to a 1.9.x line: collection × planned. */
    COMPARADO,
    /** Separate apportionment and other funds without a budget line: movement only, no difference (Q22). */
    SEM_PREVISTO_NA_PO,
    /** 1.9.x line without a linked fund: no numbers (RF-03.1.9). */
    LINHA_SEM_FUNDO,
    /** Cash flow saved before fee receipts existed (v2): reprocess the cash flow. */
    REPROCESSAR_FLUXO
}
