package br.com.condominioauditoria.api.dto.response.budget;

import java.math.BigDecimal;
import java.util.UUID;

/** Chart 5: cumulative difference of a line (actual − planned). */
public record LineDifferenceResponse(
        UUID lineId,
        String code,
        String description,
        BigDecimal planned,
        BigDecimal actual,
        BigDecimal difference,
        String target) {
}
