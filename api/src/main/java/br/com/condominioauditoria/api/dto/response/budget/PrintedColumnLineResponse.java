package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

/** A line of the column. {@code percentageText}: the "%" printed in the budget, as read. */
public record PrintedColumnLineResponse(
        @JsonProperty("linhaId") UUID lineId,
        @JsonProperty("codigo") String code,
        @JsonProperty("conta") String account,
        @JsonProperty("descricao") String description,
        @JsonProperty("valor") BigDecimal amount,
        @JsonProperty("percentualTexto") String percentageText) {
}
