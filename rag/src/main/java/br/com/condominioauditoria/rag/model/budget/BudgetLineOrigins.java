package br.com.condominioauditoria.rag.model.budget;

import java.util.List;

/**
 * Origin of the budget lines read from the support sheet of the budget (RF-13.13; ADR 0009, Decision 3 B). Same format
 * as the budgetLineOrigins block of contracts/mensagens/v4. Only explains: the planned amount is always the approved
 * budget's (O4).
 *
 * @param sheet sheet of the budget in the workbook
 * @param expensesSheet sheet with the average of each account; null when there is none
 * @param indexSheet sheet with the indexes; null when there is none
 */
public record BudgetLineOrigins(
        String sheet,
        String expensesSheet,
        String indexSheet,
        List<BudgetLineOrigin> lines) {
}
