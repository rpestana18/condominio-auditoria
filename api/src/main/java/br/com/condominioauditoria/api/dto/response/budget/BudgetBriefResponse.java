package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.BudgetStatus;
import java.util.UUID;

/** Budget used by the calculation: version, status, source file and fiscal year. */
public record BudgetBriefResponse(
        UUID id,
        Integer version,
        BudgetStatus status,
        UUID fileId,
        String fileName,
        String sha256,
        String fiscalYearStart,
        String fiscalYearEnd) {
}
