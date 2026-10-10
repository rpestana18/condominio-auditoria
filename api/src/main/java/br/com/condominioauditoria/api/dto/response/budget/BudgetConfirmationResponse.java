package br.com.condominioauditoria.api.dto.response.budget;

import java.util.UUID;

/** Data given by the Admin at confirmation; null before it. */
public record BudgetConfirmationResponse(
        UUID minutesFileId,
        boolean withoutMinutes,
        java.time.LocalDate approvalDate,
        boolean discrepancyAcknowledged,
        String discrepancyJustification) {
}
