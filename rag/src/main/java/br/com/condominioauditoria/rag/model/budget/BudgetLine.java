package br.com.condominioauditoria.rag.model.budget;

import br.com.condominioauditoria.rag.model.enums.BudgetLineMark;
import br.com.condominioauditoria.rag.model.enums.BudgetLineType;
import java.math.BigDecimal;

/**
 * A budget line. {@code sequence} is the reading order and tells apart lines with the same printed code. "%" and
 * Observações stay as the text read and are not used in calculations.
 */
public record BudgetLine(
        int sequence,
        int page,
        BudgetLineType type,
        String printedCode,
        String account,
        String accountText,
        BudgetLineMark mark,
        String description,
        BigDecimal previousBudgeted,
        BigDecimal budgeted,
        String percentageText,
        String notes) {
}
