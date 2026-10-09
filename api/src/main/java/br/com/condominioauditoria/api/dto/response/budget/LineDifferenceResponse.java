package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

/** Chart 5: cumulative difference of a line (actual − planned). */
public record LineDifferenceResponse(
        @JsonProperty("linhaId") UUID lineId,
        @JsonProperty("codigo") String code,
        @JsonProperty("descricao") String description,
        @JsonProperty("previsto") BigDecimal planned,
        @JsonProperty("realizado") BigDecimal actual,
        @JsonProperty("diferenca") BigDecimal difference,
        @JsonProperty("alvo") String target) {
}
