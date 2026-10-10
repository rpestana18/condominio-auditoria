package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.MonthStatus;
import java.math.BigDecimal;

/** Chart 2: monthly overrun as a % of planned, limit and maximum scenario (RF-03.1.11). */
public record Rule20PointResponse(
        String month,
        MonthStatus status,
        BigDecimal overrun,
        BigDecimal percentage,
        BigDecimal limitPercentage,
        BigDecimal maxScenario,
        BigDecimal maxScenarioPercentage,
        Boolean aboveLimit,
        Boolean provisional,
        String target) {
}
