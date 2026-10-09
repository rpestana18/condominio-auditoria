package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.AccountMappingAction;
import br.com.condominioauditoria.api.model.enums.AccountMappingSource;
import br.com.condominioauditoria.api.model.enums.AccountMappingStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/** An entry of the account mapping trail, with the targets as text. */
public record AccountMappingEventResponse(
        UUID id,
        @JsonProperty("conta") String account,
        @JsonProperty("nome") String name,
        @JsonProperty("acao") AccountMappingAction action,
        @JsonProperty("usuario") String username,
        @JsonProperty("em") Instant at,
        @JsonProperty("destinoAnterior") String previousTarget,
        @JsonProperty("estadoAnterior") AccountMappingStatus previousStatus,
        @JsonProperty("destinoNovo") String newTarget,
        @JsonProperty("estadoNovo") AccountMappingStatus newStatus,
        @JsonProperty("origem") AccountMappingSource source,
        @JsonProperty("motivo") String reason) {
}
