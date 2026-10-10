package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.BudgetStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Budget in the list of the condominium's budgets (contracts/openapi.yaml). Months in the YYYY-MM format. */
public record BudgetSummaryResponse(
        UUID id,
        UUID fileId,
        String fileName,
        String sha256,
        BudgetStatus status,
        Integer version,
        String title,
        String printedFiscalYear,
        String fiscalYearStart,
        String fiscalYearEnd,
        BigDecimal printedTotal,
        BigDecimal monthlyPlanned,
        Instant readAt,
        String confirmedBy,
        Instant confirmedAt,
        BudgetExtensionResponse extension) {
}
