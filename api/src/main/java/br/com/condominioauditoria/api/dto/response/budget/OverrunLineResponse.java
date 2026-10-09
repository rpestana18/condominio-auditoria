package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

/** Line that went over planned in the month (the 20% rule). */
public record OverrunLineResponse(
        @JsonProperty("linhaId") UUID lineId,
        @JsonProperty("codigo") String code,
        @JsonProperty("descricao") String description,
        @JsonProperty("excesso") BigDecimal overrun) {
}
