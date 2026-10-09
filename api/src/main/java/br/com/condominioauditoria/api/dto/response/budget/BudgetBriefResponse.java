package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.BudgetStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** Budget used by the calculation: version, status, source file and fiscal year. */
public record BudgetBriefResponse(
        UUID id,
        @JsonProperty("versao") Integer version,
        @JsonProperty("estado") BudgetStatus status,
        @JsonProperty("arquivoId") UUID fileId,
        @JsonProperty("arquivoNome") String fileName,
        String sha256,
        @JsonProperty("exercicioInicio") String fiscalYearStart,
        @JsonProperty("exercicioFim") String fiscalYearEnd) {
}
