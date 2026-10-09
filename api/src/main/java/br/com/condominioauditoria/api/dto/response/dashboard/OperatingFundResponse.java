package br.com.condominioauditoria.api.dto.response.dashboard;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Closing balance of the operating fund on the home screen card (RF-05.1a and RF-05.1b). {@code confirmed} is false
 * while it is only a suggestion.
 *
 * @param closingBalance null when the confirmed fund does not appear in the report
 */
public record OperatingFundResponse(
        @JsonProperty("fundoId") UUID fundId,
        @JsonProperty("fundo") String fund,
        @JsonProperty("confirmado") boolean confirmed,
        @JsonProperty("saldoAtual") BigDecimal closingBalance) {
}
