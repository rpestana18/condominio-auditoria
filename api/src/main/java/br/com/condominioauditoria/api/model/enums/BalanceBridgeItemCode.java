package br.com.condominioauditoria.api.model.enums;

/**
 * Items of the balance bridge (RF-13.6), in screen order. The items always add up to the balance variation; whatever
 * they do not explain goes to {@link #UNEXPLAINED}, never hidden.
 */
public enum BalanceBridgeItemCode {
    /** 1. Balance of the MONTHLY and ANNUAL lines (planned − actual). */
    LINE_SAVINGS,
    /** 2. Balance of the PROVISION lines: planned for a future payment, never counted as savings (Q44). */
    PROVISIONS_RESERVED,
    /** Balance of the SEPARATE_APPORTIONMENT lines (planned − actual; Q45). */
    SEPARATE_APPORTIONMENT,
    /**
     * 3. Income credits (fee received and other income) − planned expense of the period. In the bridge of all funds
     * (Q49), only the operating fund: "cota ordinária acima ou abaixo do previsto".
     */
    INCOME_VS_PLANNED,
    /**
     * Planned collection of the 1.9 lines, kept in the linked fund (only in the bridge of one fund other than the
     * operating fund; not in the bridge of all funds since Q49).
     */
    FUND_PLANNED_COLLECTION,
    /**
     * Debits that are not transfers, in the chosen fund other than the operating fund (they have no account mapping;
     * not in the bridge of all funds since Q49).
     */
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
