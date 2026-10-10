package br.com.condominioauditoria.api.dto.response.budget;

import java.util.List;
import java.util.UUID;

/** Account mapping screen of a budget version (contracts/openapi.yaml). */
public record AccountMappingsResponse(
        UUID budgetId,
        Integer version,
        AccountMappingSummaryResponse summary,
        List<AccountMappingResponse> accounts) {
}
