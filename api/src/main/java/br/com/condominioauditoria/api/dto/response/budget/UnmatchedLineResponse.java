package br.com.condominioauditoria.api.dto.response.budget;

import java.math.BigDecimal;
import java.util.UUID;

/** Line without a confirmed budget item: shown on its own, with the value of its fiscal year. */
public record UnmatchedLineResponse(
        String fiscalYearId,
        UUID lineId,
        String code,
        String account,
        String description,
        String group,
        BigDecimal monthlyPlanned,
        BigDecimal planned,
        BigDecimal actual,
        String target) {
}
