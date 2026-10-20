package br.com.condominioauditoria.api.model.enums;

/** State of one month in a monthly series (RF-12). */
public enum MonthlyPointStatus {
    /** The month has one cash flow and the amount comes from it. */
    LOADED,
    /** No cash flow covers the month ("sem fluxo carregado"). */
    NO_CASH_FLOW,
    /** Two cash flows cover the month: no amount until one is replaced or removed. */
    TWO_CASH_FLOWS,
    /** The cash flow of the month has no entry for the series ("nenhum pagamento no mês"). */
    NO_ENTRY
}
