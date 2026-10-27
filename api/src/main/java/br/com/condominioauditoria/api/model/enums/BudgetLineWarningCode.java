package br.com.condominioauditoria.api.model.enums;

/**
 * Warnings of the budget line review (RF-13.12; ADR 0009, Decision 1), calculated on query by
 * {@code BudgetLineReviewCheck}. Information for the person who reviews; never a finding, never blocks.
 */
public enum BudgetLineWarningCode {
    /** O1: the printed account and the description have no word in common. */
    ACCOUNT_DESCRIPTION_MISMATCH,
    /** O2: line marked by the Admin as citing a group account. */
    GROUP_ACCOUNT,
    /** O2: the account name looks like a group of accounts (suggestion; the Admin confirms the mark). */
    GROUP_ACCOUNT_SUGGESTED,
    /** The same account code in more than one line. */
    ACCOUNT_IN_TWO_LINES,
    /** The same printed item in more than one line (RF-13.2). */
    REPEATED_ITEM,
    /** Budgeted amount zero: any expense in the line is an overrun. */
    ZERO_BUDGETED,
    /** The final amount of the support sheet differs from the budget's amount (the budget's amount holds, O4). */
    ORIGIN_FINAL_DIFFERS
}
