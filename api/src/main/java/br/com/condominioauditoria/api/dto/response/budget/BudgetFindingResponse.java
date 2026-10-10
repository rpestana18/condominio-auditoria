package br.com.condominioauditoria.api.dto.response.budget;

import java.util.UUID;

/** Finding raised on the budget itself (e.g. the reserve fund cap). */
public record BudgetFindingResponse(
        UUID id,
        String rule,
        String ruleVersion,
        String severity,
        String referenceMonth,
        String description,
        String status) {
}
