package br.com.condominioauditoria.api.dto.response.budget;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Budget group with planned, actual, difference and execution, and its lines. */
public record BudgetVsActualGroupResponse(
        UUID lineId,
        String code,
        String description,
        BigDecimal planned,
        BigDecimal actual,
        BigDecimal difference,
        BigDecimal execution,
        List<BudgetVsActualLineResponse> lines) {
}
