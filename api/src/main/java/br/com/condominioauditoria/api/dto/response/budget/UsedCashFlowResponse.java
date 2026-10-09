package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Cash flow file used by the calculation, with period, hash and upload. */
public record UsedCashFlowResponse(
        @JsonProperty("arquivoId") UUID fileId,
        @JsonProperty("nome") String name,
        String sha256,
        @JsonProperty("periodoInicio") LocalDate periodStart,
        @JsonProperty("periodoFim") LocalDate periodEnd,
        @JsonProperty("enviadoEm") Instant uploadedAt,
        @JsonProperty("enviadoPor") String uploadedBy) {
}
