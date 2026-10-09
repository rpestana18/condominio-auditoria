package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Budget group with planned, actual, difference and execution, and its lines. */
public record BudgetVsActualGroupResponse(
        @JsonProperty("linhaId") UUID lineId,
        @JsonProperty("codigo") String code,
        @JsonProperty("descricao") String description,
        @JsonProperty("previsto") BigDecimal planned,
        @JsonProperty("realizado") BigDecimal actual,
        @JsonProperty("diferenca") BigDecimal difference,
        @JsonProperty("execucao") BigDecimal execution,
        @JsonProperty("linhas") List<BudgetVsActualLineResponse> lines) {
}
