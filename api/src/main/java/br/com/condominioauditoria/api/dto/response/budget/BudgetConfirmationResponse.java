package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** Data given by the Admin at confirmation; null before it. */
public record BudgetConfirmationResponse(
        @JsonProperty("ataArquivoId") UUID minutesFileId,
        @JsonProperty("semAta") boolean withoutMinutes,
        @JsonProperty("dataAprovacao") java.time.LocalDate approvalDate,
        @JsonProperty("cienteDivergencia") boolean discrepancyAcknowledged,
        @JsonProperty("justificativaDivergencia") String discrepancyJustification) {
}
