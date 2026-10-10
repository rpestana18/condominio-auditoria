package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.MonthStatus;
import java.math.BigDecimal;

/** A month of chart 6: planned and collected of a fund. */
public record FundPointResponse(
        String month,
        MonthStatus status,
        BigDecimal planned,
        BigDecimal collected,
        String target) {
}
