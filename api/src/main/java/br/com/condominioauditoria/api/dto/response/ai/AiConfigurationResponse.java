package br.com.condominioauditoria.api.dto.response.ai;

import br.com.condominioauditoria.api.model.enums.AiMode;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * The condominium's AI configuration (contracts/openapi.yaml, ConfiguracaoIa). Never carries the key, not even
 * encrypted: only whether it is registered and its last 4 characters.
 */
public record AiConfigurationResponse(
        @JsonProperty("modoGeral") AiMode generalMode,
        @JsonProperty("assistente") AiAssistantResponse assistant,
        @JsonProperty("atualizadoPor") String updatedBy,
        @JsonProperty("atualizadoEm") Instant updatedAt) {
}
