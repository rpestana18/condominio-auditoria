package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.MonthStatus;
import java.math.BigDecimal;

/** A month of a chart series, with the value and the evidence target. */
public record ValuePointResponse(
        String month,
        MonthStatus status,
        BigDecimal amount,
        String target) {
}
