package br.com.condominioauditoria.api.dto.response.audit;

import br.com.condominioauditoria.api.model.enums.FindingStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record FindingEventResponse(
        @JsonProperty("estadoAnterior") FindingStatus previousStatus,
        @JsonProperty("estadoNovo") FindingStatus newStatus,
        @JsonProperty("condicaoPresente") boolean conditionPresent,
        @JsonProperty("motivo") String reason,
        @JsonProperty("usuario") String username,
        @JsonProperty("em") Instant occurredAt) {
}
