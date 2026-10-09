package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;

/** "N of M accounts confirmed": accounts with a debit in the Condomínio fund in the period. */
public record PeriodMappingSummaryResponse(
        @JsonProperty("contas") int accounts,
        @JsonProperty("confirmadas") int confirmed,
        @JsonProperty("semDeparaConfirmado") int withoutConfirmedMapping) {
}
