package br.com.condominioauditoria.api.dto.request.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Budget confirmation by the Admin (RF-03.1.3, RF-03.1.2 and Q29). Months in the YYYY-MM format.
 *
 * @param minutesFileId file of the MINUTES category that approved the budget; null with {@code withoutMinutes}
 *
 * @param approvalDate date of the meeting (required with minutes); without it, the fiscal year start applies
 *
 * @param effectiveCodes distinct code for lines whose printed code repeats
 *
 * @param funds link of each fund line (1.9.x) to a fund of the cash flow
 *
 * @param reapproval supersedes the confirmed budget valid in the same months (new version)
 *
 * @param discrepancyAcknowledged confirms a budget read with a sum discrepancy, with {@code justification}
 */
public record BudgetConfirmationRequest(
        @JsonProperty("exercicioInicio") String fiscalYearStart,
        @JsonProperty("exercicioFim") String fiscalYearEnd,
        @JsonProperty("ataArquivoId") UUID minutesFileId,
        @JsonProperty("semAta") boolean withoutMinutes,
        @JsonProperty("dataAprovacao") LocalDate approvalDate,
        @JsonProperty("codigosEfetivos") List<EffectiveCodeRequest> effectiveCodes,
        @JsonProperty("fundos") List<FundLinkRequest> funds,
        @JsonProperty("reaprovacao") boolean reapproval,
        @JsonProperty("cienteDivergencia") boolean discrepancyAcknowledged,
        @JsonProperty("justificativa") String justification) {

    public List<EffectiveCodeRequest> effectiveCodes() {
        return effectiveCodes == null ? List.of() : effectiveCodes;
    }

    public List<FundLinkRequest> funds() {
        return funds == null ? List.of() : funds;
    }
}
