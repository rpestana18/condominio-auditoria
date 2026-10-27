package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.BudgetVsActualStatus;
import java.util.List;

/** Tracking grid (RF-13.4): every budget line × the months of the fiscal year, cumulative and line balance. Calculated on each request by BudgetTrackingGrid; the numbers of each month are the budget vs. actual ones. {@code lineReview}: review state of the budget lines (RF-13.12); while they are not reviewed, {@code groups} is empty and the LINES_NOT_REVIEWED warning says why. */
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
        List<BudgetVsActualWarningResponse> warnings,
        LineReviewStateResponse lineReview) {

    /** Without the review state (filled in by the service, ADR 0009 Decision 1). */
    public BudgetTrackingResponse(String calculationVersion, String budgetVsActualVersion, BudgetVsActualStatus status,
            String message, BudgetBriefResponse budget, List<TrackingMonthResponse> months,
            List<String> cumulativeMonths, List<TrackingGroupResponse> groups, TrackingCumulativeResponse cumulative,
            TrackingBlocksResponse blocks, List<BudgetVsActualWarningResponse> warnings) {
        this(calculationVersion, budgetVsActualVersion, status, message, budget, months, cumulativeMonths, groups,
                cumulative, blocks, warnings, null);
    }

    /**
     * The same grid with the review state. Lines not reviewed (RF-13.12): no per-line numbers (no groups) and the
     * LINES_NOT_REVIEWED warning; the totals and the blocks stay.
     */
    public BudgetTrackingResponse withLineReview(LineReviewStateResponse review) {
        if (review == null || review.reviewed()) {
            return new BudgetTrackingResponse(calculationVersion, budgetVsActualVersion, status, message, budget,
                    months, cumulativeMonths, groups, cumulative, blocks, warnings, review);
        }
        List<BudgetVsActualWarningResponse> w = new java.util.ArrayList<>(warnings == null ? List.of() : warnings);
        w.addFirst(new BudgetVsActualWarningResponse("LINES_NOT_REVIEWED", review.message()));
        return new BudgetTrackingResponse(calculationVersion, budgetVsActualVersion, status, message, budget, months,
                cumulativeMonths, List.of(), cumulative, blocks, List.copyOf(w), review);
    }
}
