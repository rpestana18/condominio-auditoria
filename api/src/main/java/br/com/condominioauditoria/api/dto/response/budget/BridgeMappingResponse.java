package br.com.condominioauditoria.api.dto.response.budget;

import java.math.BigDecimal;
import java.util.List;

/**
 * Account mapping state of the operating fund in the months of the bridge total (RF-03.1.10), as fields instead of
 * the text of the NO_CONFIRMED_MAPPING warning. {@code unmappedAccounts}: accounts with a code and without a link found
 * (Q54: automatic links count; an account the Admin decided has no line does not); {@code unmappedAmount}: their
 * amount in "sem linha da PO"; {@code debits}: debits of the operating fund in
 * the same months, without the transfers between funds; {@code provisional}: {@code unmappedAmount} is above
 * {@code provisionalLimitPercentage}% of {@code debits}, so "Economia nas linhas" and "Fora da PO" are not yet
 * meaningful (the bridge still closes). {@code accounts}: largest amount first, entries without an account last.
 */
public record BridgeMappingResponse(
        int unmappedAccounts,
        BigDecimal unmappedAmount,
        int unmappedEntries,
        BigDecimal debits,
        BigDecimal provisionalLimitPercentage,
        boolean provisional,
        List<BlockAccountResponse> accounts) {
}
