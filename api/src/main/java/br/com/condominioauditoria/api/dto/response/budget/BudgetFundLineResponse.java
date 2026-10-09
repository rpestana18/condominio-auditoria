package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

/** Fund line (1.9.x) of the budget and the cash flow fund it is linked to. */
public record BudgetFundLineResponse(
        @JsonProperty("linhaId") UUID lineId,
        @JsonProperty("codigoEfetivo") String effectiveCode,
        @JsonProperty("descricao") String description,
        @JsonProperty("orcado") BigDecimal budgeted,
        @JsonProperty("fundoId") UUID fundId,
        @JsonProperty("fundo") String fund) {
}
