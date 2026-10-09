package br.com.condominioauditoria.api.dto.response.audit;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record FindingEvidenceResponse(
        @JsonProperty("ordem") int position,
        @JsonProperty("arquivoId") UUID fileId,
        String sha256,
        @JsonProperty("pagina") Integer page,
        @JsonProperty("referencia") String reference,
        @JsonProperty("linhaPoId") UUID budgetLineId) {
}
