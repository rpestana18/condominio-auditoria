package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

/** Line without a confirmed budget item: shown on its own, with the value of its fiscal year. */
public record UnmatchedLineResponse(
        @JsonProperty("exercicioId") String fiscalYearId,
        @JsonProperty("linhaId") UUID lineId,
        @JsonProperty("codigo") String code,
        @JsonProperty("conta") String account,
        @JsonProperty("descricao") String description,
        @JsonProperty("grupo") String group,
        @JsonProperty("previstoMes") BigDecimal monthlyPlanned,
        @JsonProperty("previsto") BigDecimal planned,
        @JsonProperty("realizado") BigDecimal actual,
        @JsonProperty("alvo") String target) {
}
