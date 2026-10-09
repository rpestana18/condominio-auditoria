package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

/** Account mapping screen of a budget version (contracts/openapi.yaml). */
public record AccountMappingsResponse(
        @JsonProperty("previsaoId") UUID budgetId,
        @JsonProperty("versao") Integer version,
        @JsonProperty("resumo") AccountMappingSummaryResponse summary,
        @JsonProperty("contas") List<AccountMappingResponse> accounts) {
}
