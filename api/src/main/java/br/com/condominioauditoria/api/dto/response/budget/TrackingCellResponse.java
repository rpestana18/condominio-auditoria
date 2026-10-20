package br.com.condominioauditoria.api.dto.response.budget;

import java.math.BigDecimal;

/** Cell of a line or group in a month: actual and actual − planned (positive = above the planned amount, negative = below). All null when the month has no numbers ("sem extrato carregado"), never zero. */
public record TrackingCellResponse(
        String month,
        BigDecimal planned,
        BigDecimal actual,
        BigDecimal difference,
        Integer entries) {
}
