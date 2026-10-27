package br.com.condominioauditoria.rag.model.budget;

import java.math.BigDecimal;
import java.util.List;

/**
 * One expense line of the support sheet, with the method deduced from the numbers (tolerance R$ 0,01). Money with 2
 * decimals; {@code indexRate} as a decimal fraction (0.0723 = 7,23%); months as YYYY-MM.
 *
 * @param sequence order among the expense lines (groups 1.1 to 1.8), the same order as the budget's expense lines
 * @param row row of the sheet, for traceability
 * @param method AVERAGE_TIMES_INDEX, EQUAL_TO_AVERAGE, TYPED or NO_AVERAGE
 */
public record BudgetLineOrigin(
        int sequence,
        int row,
        String printedCode,
        String account,
        String description,
        List<String> sourceAccounts,
        String averageFrom,
        String averageTo,
        BigDecimal averageAmount,
        BigDecimal baseAmount,
        String method,
        String indexName,
        BigDecimal indexRate,
        boolean indexForecast,
        String indexNote,
        BigDecimal finalAmount,
        String reason) {
}
