package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.MonthStatus;
import java.math.BigDecimal;

/** Chart 1: actual ÷ monthly planned, with the 100% reference. */
public record ExecutionPointResponse(
        String month,
        MonthStatus status,
        BigDecimal planned,
        BigDecimal actual,
        BigDecimal execution,
        String target) {
}
