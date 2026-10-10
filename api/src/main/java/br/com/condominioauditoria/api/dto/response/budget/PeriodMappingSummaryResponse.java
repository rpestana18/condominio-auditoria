package br.com.condominioauditoria.api.dto.response.budget;


/** "N of M accounts confirmed": accounts with a debit in the Condomínio fund in the period. */
public record PeriodMappingSummaryResponse(
        int accounts,
        int confirmed,
        int withoutConfirmedMapping) {
}
