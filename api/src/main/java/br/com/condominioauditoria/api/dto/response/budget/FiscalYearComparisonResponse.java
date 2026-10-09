package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

/** Fiscal year comparison (RF-11.6): summary, per group, per budget item and unmatched lines. */
public record FiscalYearComparisonResponse(
        @JsonProperty("exercicios") List<ComparedFiscalYearResponse> fiscalYears,
        @JsonProperty("fundoId") UUID fundId,
        @JsonProperty("mesmosMeses") boolean sameMonths,
        @JsonProperty("comparando") String comparing,
        @JsonProperty("resumo") List<FiscalYearSummaryResponse> summary,
        @JsonProperty("grupos") List<ComparedGroupResponse> groups,
        @JsonProperty("linhas") List<ComparedItemResponse> lines,
        @JsonProperty("semCorrespondencia") List<UnmatchedLineResponse> unmatched,
        @JsonProperty("avisos") List<String> warnings) {
}
