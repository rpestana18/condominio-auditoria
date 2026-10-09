package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.BudgetStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Budget in the list of the condominium's budgets (contracts/openapi.yaml). Months in the YYYY-MM format. */
public record BudgetSummaryResponse(
        UUID id,
        @JsonProperty("arquivoId") UUID fileId,
        @JsonProperty("arquivoNome") String fileName,
        String sha256,
        @JsonProperty("estado") BudgetStatus status,
        @JsonProperty("versao") Integer version,
        @JsonProperty("titulo") String title,
        @JsonProperty("exercicioImpresso") String printedFiscalYear,
        @JsonProperty("exercicioInicio") String fiscalYearStart,
        @JsonProperty("exercicioFim") String fiscalYearEnd,
        @JsonProperty("totalImpresso") BigDecimal printedTotal,
        @JsonProperty("previstoMes") BigDecimal monthlyPlanned,
        @JsonProperty("lidaEm") Instant readAt,
        @JsonProperty("confirmadaPor") String confirmedBy,
        @JsonProperty("confirmadaEm") Instant confirmedAt,
        @JsonProperty("prorrogacao") BudgetExtensionResponse extension) {
}
