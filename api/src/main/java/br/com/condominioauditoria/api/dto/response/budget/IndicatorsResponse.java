package br.com.condominioauditoria.api.dto.response.budget;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code start} and {@code end}: fiscal year months (YYYY-MM). {@code dataAsOf}: latest upload of the cash flows used.
 */
public record IndicatorsResponse(
        UUID budgetId,
        String label,
        String start,
        String end,
        UUID fundId,
        Instant dataAsOf,
        BigDecimal limitPercentage,
        List<ExecutionPointResponse> monthlyExecution,
        List<Rule20PointResponse> rule20,
        List<CumulativePointResponse> cumulative,
        List<GroupSeriesResponse> actualByGroup,
        LargestDifferencesResponse largestDifferences,
        List<FundSeriesResponse> funds,
        IndicatorComparisonResponse comparison,
        List<String> warnings) {
}
