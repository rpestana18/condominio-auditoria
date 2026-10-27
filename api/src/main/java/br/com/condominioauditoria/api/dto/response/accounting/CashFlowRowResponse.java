package br.com.condominioauditoria.api.dto.response.accounting;

import br.com.condominioauditoria.api.model.enums.CashFlowRowCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A row of "Entradas e saídas": one amount per month in {@code cells}, in the order of the months (null = no number in
 * that month: no cash flow, or the row does not apply to it). {@code total}: sum of the cells, null when every cell is
 * null. A budget line row carries {@code lineId}; an account row carries {@code account}; a fund row {@code fundId}.
 * {@code children}: the accounts of a block, the funds of "pago por outros fundos".
 */
public record CashFlowRowResponse(
        CashFlowRowCode code,
        String label,
        UUID lineId,
        String lineCode,
        String account,
        UUID fundId,
        List<BigDecimal> cells,
        BigDecimal total,
        List<CashFlowRowResponse> children) {
}
