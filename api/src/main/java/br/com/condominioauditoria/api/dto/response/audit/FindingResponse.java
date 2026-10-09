package br.com.condominioauditoria.api.dto.response.audit;

import br.com.condominioauditoria.api.model.enums.FindingStatus;
import br.com.condominioauditoria.api.model.enums.Severity;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FindingResponse(
        UUID id,
        @JsonProperty("regra") String rule,
        @JsonProperty("versaoRegra") String ruleVersion,
        @JsonProperty("severidade") Severity severity,
        @JsonProperty("competencia") String referenceMonth,
        @JsonProperty("alvo") String target,
        @JsonProperty("descricao") String description,
        @JsonProperty("estado") FindingStatus status,
        @JsonProperty("estadoMotivo") String statusReason,
        @JsonProperty("estadoEm") Instant statusChangedAt,
        @JsonProperty("condicaoPresente") boolean conditionPresent,
        @JsonProperty("criadoEm") Instant createdAt,
        @JsonProperty("evidencias") List<FindingEvidenceResponse> evidence,
        @JsonProperty("historico") List<FindingEventResponse> history) {
}
