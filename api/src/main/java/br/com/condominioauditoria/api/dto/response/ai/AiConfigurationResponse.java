package br.com.condominioauditoria.api.dto.response.ai;

import br.com.condominioauditoria.api.model.enums.AiMode;
import java.time.Instant;

/**
 * The condominium's AI configuration (contracts/openapi.yaml, ConfiguracaoIa). Never carries the key, not even
 * encrypted: only whether it is registered and its last 4 characters.
 */
public record AiConfigurationResponse(
        AiMode generalMode,
        AiAssistantResponse assistant,
        String updatedBy,
        Instant updatedAt) {
}
