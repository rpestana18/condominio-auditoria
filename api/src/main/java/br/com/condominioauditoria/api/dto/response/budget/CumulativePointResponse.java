package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.MonthStatus;
import java.math.BigDecimal;

/** Chart 3: planned and actual summed up to the month (only months with a cash flow enter the sum). */
public record CumulativePointResponse(
        String month,
        MonthStatus status,
        BigDecimal cumulativePlanned,
        BigDecimal cumulativeActual,
        String target) {
}
