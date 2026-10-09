package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/** Account inside a separate block, with its amount and number of entries. */
public record BlockAccountResponse(
        @JsonProperty("conta") String account,
        @JsonProperty("nome") String name,
        @JsonProperty("detalhe") String detail,
        @JsonProperty("valor") BigDecimal amount,
        @JsonProperty("lancamentos") int entries) {
}
