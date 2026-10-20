package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.BudgetVsActualStatus;
import java.util.List;

/** Tracking grid (RF-13.4): every budget line × the months of the fiscal year, cumulative and line balance. Calculated on each request by BudgetTrackingGrid; the numbers of each month are the budget vs. actual ones. */
public record BudgetTrackingResponse(
        String calculationVersion,
        String budgetVsActualVersion,
        BudgetVsActualStatus status,
        String message,
        BudgetBriefResponse budget,
        List<TrackingMonthResponse> months,
        List<String> cumulativeMonths,
        List<TrackingGroupResponse> groups,
        TrackingCumulativeResponse cumulative,
        TrackingBlocksResponse blocks,
        List<BudgetVsActualWarningResponse> warnings) {
}
