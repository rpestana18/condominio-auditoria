package br.com.condominioauditoria.api.model.enums;

/**
 * Items of the balance bridge (RF-13.15; ADR 0009, Decision 5: version 5), in screen order. The items always add up to
 * the balance variation; whatever they do not explain goes to {@link #UNEXPLAINED}, never hidden.
 * {@code LINE_SAVINGS + PROVISIONS_RESERVED} is the planned − actual of the budget lines of the
 * period (the separate apportionment lines apart, in {@link #SEPARATE_APPORTIONMENT}).
 */
public enum BalanceBridgeItemCode {
    /**
     * 1. Planned − actual of the RECURRING, SEASONAL, OCCASIONAL and ANNUAL lines (Q52, answered on 28/10/2026): the only
     * place the word "economia" is used. Each line of its list carries its profile ("eventual"/"anual" on the screen).
     */
    LINE_SAVINGS,
    /** 2. Planned − actual of the PROVISION lines: planned for a future payment, never counted as savings (Q44). */
    PROVISIONS_RESERVED,
    /** Planned − actual of the SEPARATE_APPORTIONMENT lines (zero budget; Q45). */
    SEPARATE_APPORTIONMENT,
    /**
     * 3. Income credits (fee received and other income) − planned expense of the period (N2; replaces
     * INCOME_VS_PLANNED). In the bridge of all funds (Q49), only the operating fund: "cota ordinária acima da despesa
     * prevista".
     */
    INCOME_ABOVE_PLANNED_EXPENSE,
    /**
     * Planned collection of the 1.9 lines, kept in the linked fund (only in the bridge of one fund other than the
     * operating fund).
     */
    FUND_PLANNED_COLLECTION,
    /** Debits that are not transfers, in the chosen fund other than the operating fund. */
    FUND_EXPENSES,
    /** 4. To reallocate, without budget line and adjustments, negative when they leave the fund. */
    OUTSIDE_BUDGET,
    /** 5. Transfers between funds received − sent. */
    INTER_FUND_TRANSFERS,
    /**
     * Only in the bridge of all funds (Q49): credits − debits of the apportionment funds (every fund that is not the
     * operating fund), without the entries marked as transfers between funds. No share of the variation.
     */
    APPORTIONMENT_FUNDS,
    /** 6. Real variation − sum of the items above. Should be zero; otherwise "a ponte não fecha". */
    UNEXPLAINED
}
