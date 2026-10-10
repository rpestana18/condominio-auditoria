package br.com.condominioauditoria.api.dto.response.budget;

import java.math.BigDecimal;
import java.util.List;

/** Value of a fiscal year in a group or budget item; {@code target} opens the evidence in budget vs. actual. */
public record ComparedValueResponse(
        String fiscalYearId,
        BigDecimal monthlyPlanned,
        BigDecimal planned,
        BigDecimal actual,
        VariationResponse monthlyPlannedVariation,
        VariationResponse actualVariation,
        String target,
        List<UsedLineResponse> lines) {
}
