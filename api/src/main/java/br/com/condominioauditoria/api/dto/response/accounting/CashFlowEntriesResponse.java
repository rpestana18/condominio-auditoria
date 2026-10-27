package br.com.condominioauditoria.api.dto.response.accounting;

import br.com.condominioauditoria.api.model.enums.CashFlowRowCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Entries list of "Entradas e saídas" with the filters applied (fund, month, line, block, account), in date order.
 * {@code total}: sum of the amounts listed. {@code message}: why the list is empty when there is no cash flow.
 */
public record CashFlowEntriesResponse(
        UUID fundId,
        boolean allFunds,
        String month,
        UUID lineId,
        CashFlowRowCode block,
        String account,
        int count,
        BigDecimal total,
        String message,
        List<CashFlowEntryResponse> entries) {
}
