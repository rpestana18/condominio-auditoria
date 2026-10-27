package br.com.condominioauditoria.api.model.enums;

/**
 * Rows of the "Entradas e saídas" screen (RF-13.11; ADR 0009, Decision 4) and the filter of its entries list. The
 * budget lines are rows too ({@link #BUDGET_LINE}, with the line id).
 */
public enum CashFlowRowCode {
    /** Inflows: fee received (Q25 mark). */
    CONDO_FEE,
    /** Inflows: other income of the fund. */
    OTHER_INCOME,
    /** Inflows: credits without the fee mark (old read). */
    OTHER_CREDITS,
    /** Inflows of the funds other than the operating fund (extra fees, apportionments; D5), in the view of all funds. */
    OTHER_FUNDS_INCOME,
    /** Outflows: a budget line. */
    BUDGET_LINE,
    /** Outflows: lines of the separate apportionment (utilities; Q45, N3). */
    SEPARATE_APPORTIONMENT,
    WITHOUT_BUDGET_LINE,
    TO_REALLOCATE,
    ADJUSTMENTS,
    /** Outflows paid by the other funds (view of all funds). */
    OTHER_FUNDS,
    /** Outflows by cash flow account: before the budget, or while the budget lines are not reviewed. */
    BY_ACCOUNT,
    TRANSFERS_IN,
    TRANSFERS_OUT
}
