package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code start} and {@code end}: fiscal year months (YYYY-MM). {@code dataAsOf}: latest upload of the cash flows used.
 */
public record IndicatorsResponse(
        @JsonProperty("poId") UUID budgetId,
        @JsonProperty("rotulo") String label,
        @JsonProperty("inicio") String start,
        @JsonProperty("fim") String end,
        @JsonProperty("fundoId") UUID fundId,
        @JsonProperty("dadosDe") Instant dataAsOf,
        @JsonProperty("limitePercentual") BigDecimal limitPercentage,
        @JsonProperty("execucaoMensal") List<ExecutionPointResponse> monthlyExecution,
        @JsonProperty("regra20") List<Rule20PointResponse> rule20,
        @JsonProperty("acumulado") List<CumulativePointResponse> cumulative,
        @JsonProperty("realizadoPorGrupo") List<GroupSeriesResponse> actualByGroup,
        @JsonProperty("maioresDiferencas") LargestDifferencesResponse largestDifferences,
        @JsonProperty("fundos") List<FundSeriesResponse> funds,
        @JsonProperty("comparacao") IndicatorComparisonResponse comparison,
        @JsonProperty("avisos") List<String> warnings) {
}
