package br.com.condominioauditoria.rag.model.budget;

import java.util.List;

/**
 * Approved budget, already read, as it is in the document: each line with the code as printed and the values read,
 * with no judgment. Same format as the previsaoOrcamentaria block of contracts/mensagens/v3.
 *
 * @param budgetColumns printed labels of the budgeted columns, in the order [previous, fiscal year]
 */
public record Budget(
        String title,
        String printedFiscalYear,
        List<String> budgetColumns,
        List<BudgetLine> lines) {
}
