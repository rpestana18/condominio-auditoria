package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** Finding raised on the budget itself (e.g. the reserve fund cap). */
public record BudgetFindingResponse(
        UUID id,
        @JsonProperty("regra") String rule,
        @JsonProperty("versaoRegra") String ruleVersion,
        @JsonProperty("severidade") String severity,
        @JsonProperty("competencia") String referenceMonth,
        @JsonProperty("descricao") String description,
        @JsonProperty("estado") String status) {
}
