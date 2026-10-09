package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

/** Value of a fiscal year in a group or budget item; {@code target} opens the evidence in budget vs. actual. */
public record ComparedValueResponse(
        @JsonProperty("exercicioId") String fiscalYearId,
        @JsonProperty("previstoMes") BigDecimal monthlyPlanned,
        @JsonProperty("previsto") BigDecimal planned,
        @JsonProperty("realizado") BigDecimal actual,
        @JsonProperty("variacaoPrevistoMes") VariationResponse monthlyPlannedVariation,
        @JsonProperty("variacaoRealizado") VariationResponse actualVariation,
        @JsonProperty("alvo") String target,
        @JsonProperty("linhas") List<UsedLineResponse> lines) {
}
