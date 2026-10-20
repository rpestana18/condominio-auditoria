package br.com.condominioauditoria.api.dto.response.budget;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Group of the budget (1.1 to 1.8) with the subtotal per month (sum of its lines) and its lines in document order. */
public record TrackingGroupResponse(
        UUID lineId,
        String code,
        String description,
        List<TrackingCellResponse> cells,
        BigDecimal cumulativePlanned,
        BigDecimal cumulativeActual,
        BigDecimal balance,
        List<TrackingLineResponse> lines) {
}
