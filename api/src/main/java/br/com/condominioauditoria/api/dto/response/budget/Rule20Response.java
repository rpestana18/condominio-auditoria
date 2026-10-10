package br.com.condominioauditoria.api.dto.response.budget;

import java.math.BigDecimal;
import java.util.List;

/**
 * The 20% rule (monthly only). {@code limit} rounded to cents; the comparison is exact (MonthlyOverrunRule).
 * {@code maxScenario} = overrun + to reallocate + without budget line (Q26).
 */
public record Rule20Response(
        String rule,
        String ruleVersion,
        BigDecimal limitPercentage,
        BigDecimal monthlyPlanned,
        BigDecimal overrun,
        BigDecimal percentage,
        BigDecimal limit,
        int linesAbove,
        List<OverrunLineResponse> lines,
        BigDecimal toReallocate,
        BigDecimal withoutBudgetLine,
        BigDecimal maxScenario,
        BigDecimal maxScenarioPercentage,
        boolean provisional,
        boolean aboveLimit) {
}
