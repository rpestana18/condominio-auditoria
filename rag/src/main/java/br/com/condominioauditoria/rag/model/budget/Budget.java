package br.com.condominioauditoria.rag.model.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Approved budget, already read, as it is in the document: each line with the code as printed and the values read,
 * with no judgment. Same format as the previsaoOrcamentaria block of contracts/mensagens/v2 (ADR 0004, Decision 2).
 *
 * @param budgetColumns printed labels of the budgeted columns, in the order [previous, fiscal year]
 */
public record Budget(
        @JsonProperty("titulo") String title,
        @JsonProperty("exercicioImpresso") String printedFiscalYear,
        @JsonProperty("colunasOrcado") List<String> budgetColumns,
        @JsonProperty("linhas") List<BudgetLine> lines) {
}
