package br.com.condominioauditoria.api.dto.response.budget;


/**
 * Accounts with a debit in the Condomínio fund in the period: {@code confirmed} = with a link that counts (automatic or
 * confirmed, Q54); {@code withoutConfirmedMapping} = without a link found (the Admin's "sem linha da PO" not included).
 */
public record PeriodMappingSummaryResponse(
        int accounts,
        int confirmed,
        int withoutConfirmedMapping) {
}
