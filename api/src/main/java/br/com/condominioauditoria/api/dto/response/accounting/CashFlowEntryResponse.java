package br.com.condominioauditoria.api.dto.response.accounting;

import br.com.condominioauditoria.api.model.enums.CashFlowRowCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An entry of the cash flow with its destination (RF-13.11, RF-03.1.12): the budget line ({@code destination}
 * BUDGET_LINE or SEPARATE_APPORTIONMENT, with the line) or the block, with the source file, page and hash.
 * {@code direction}: OUT (debit) or IN (credit).
 */
public record CashFlowEntryResponse(
        UUID entryId,
        LocalDate date,
        String month,
        String memo,
        String supplier,
        String document,
        String account,
        String accountName,
        UUID fundId,
        String fund,
        BigDecimal amount,
        String direction,
        CashFlowRowCode destination,
        UUID lineId,
        String lineCode,
        String lineDescription,
        String destinationLabel,
        UUID fileId,
        String fileName,
        String sha256,
        int page,
        int position) {
}
