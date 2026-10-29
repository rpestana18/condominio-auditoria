package br.com.condominioauditoria.rag.model.budget;

/**
 * One account of the expenses sheet of the budget's support sheet (contracts/mensagens/v5; ADR 0009, Decision 8). It
 * is in the budget's chart of accounts, never in the cash flow's: the api compares only the names (RF-00.8).
 *
 * @param code account code printed in the sheet (e.g. 1547)
 * @param name name printed after the code (e.g. Adiantamento Quinzenal)
 * @param row row of the sheet, for traceability
 * @param parentCode group account deduced from the sums (the account is one of the consecutive accounts that add up,
 *     month by month and to the cent, to the group's amount), or null
 */
public record ExpenseAccount(String code, String name, int row, String parentCode) {
}
