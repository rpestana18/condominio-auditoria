package br.com.condominioauditoria.api.dto.response.accounting;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** A budget group (1.1 to 1.8) in the outflows, with the sum of its lines per month and its lines in budget order. */
public record CashFlowGroupResponse(
        UUID lineId,
        String code,
        String description,
        List<BigDecimal> cells,
        BigDecimal total,
        List<CashFlowRowResponse> lines) {
}
