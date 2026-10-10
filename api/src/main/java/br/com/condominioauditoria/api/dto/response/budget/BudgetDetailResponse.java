package br.com.condominioauditoria.api.dto.response.budget;

import java.math.BigDecimal;
import java.util.List;

/** Budget detail for the PO screen: lines, checks, warnings, repeated codes, fund links and findings. */
public record BudgetDetailResponse(
        BudgetSummaryResponse budget,
        String previousBudgetedColumn,
        String budgetedColumn,
        BigDecimal printedMonthlyPlanned,
        BigDecimal roundingTolerance,
        String supersededFrom,
        BudgetConfirmationResponse confirmation,
        List<BudgetLineResponse> lines,
        List<BudgetCheckResponse> checks,
        List<BudgetWarningResponse> warnings,
        List<RepeatedCodeResponse> repeatedCodes,
        List<BudgetFundLineResponse> funds,
        List<BudgetFindingResponse> findings) {
}
