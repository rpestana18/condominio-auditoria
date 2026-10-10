package br.com.condominioauditoria.api.dto.response.budget;

import java.util.List;
import java.util.UUID;

/** Fiscal year comparison (RF-11.6): summary, per group, per budget item and unmatched lines. */
public record FiscalYearComparisonResponse(
        List<ComparedFiscalYearResponse> fiscalYears,
        UUID fundId,
        boolean sameMonths,
        String comparing,
        List<FiscalYearSummaryResponse> summary,
        List<ComparedGroupResponse> groups,
        List<ComparedItemResponse> lines,
        List<UnmatchedLineResponse> unmatched,
        List<String> warnings) {
}
