package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Chart 7: monthly planned per group in each fiscal year and the cumulative execution of each (RF-11.6). Values in
 * the order of {@code fiscalYears}.
 */
public record IndicatorComparisonResponse(
        @JsonProperty("exercicios") List<ComparisonFiscalYearResponse> fiscalYears,
        @JsonProperty("grupos") List<ComparisonGroupResponse> groups) {
}
