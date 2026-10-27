package br.com.condominioauditoria.api.dto.response.accounting;

import br.com.condominioauditoria.api.dto.response.budget.BudgetBriefResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualWarningResponse;
import br.com.condominioauditoria.api.dto.response.budget.LineReviewStateResponse;
import java.util.List;
import java.util.UUID;

/**
 * "Entradas e saídas" (RF-13.11 and RF-13.16; ADR 0009, Decision 4): inflows, outflows by budget line and by block,
 * transfers and balances, months side by side. Calculated on each request by MonthlyCashFlow; nothing is stored. Every
 * row has one cell per entry of {@code months}. {@code fundId} null with {@code allFunds}: every fund of the cash flows.
 * {@code lineReview}: while the budget lines are not reviewed (RF-13.12), the outflows come by cash flow account.
 */
public record CashFlowMonthlyResponse(
        String calculationVersion,
        UUID fundId,
        String fundName,
        boolean allFunds,
        String from,
        String to,
        BudgetBriefResponse budget,
        LineReviewStateResponse lineReview,
        List<CashFlowMonthResponse> months,
        List<CashFlowRowResponse> inflows,
        List<CashFlowGroupResponse> outflowGroups,
        List<CashFlowRowResponse> outflowBlocks,
        List<CashFlowRowResponse> transfers,
        List<BudgetVsActualWarningResponse> warnings) {
}
