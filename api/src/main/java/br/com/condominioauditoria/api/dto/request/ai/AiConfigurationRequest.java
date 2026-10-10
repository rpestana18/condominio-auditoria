package br.com.condominioauditoria.api.dto.request.ai;

import br.com.condominioauditoria.api.model.enums.AiMode;

/** PUT /condominiums/{id}/ai (contracts/openapi.yaml, AiConfigurationRequest). */
public record AiConfigurationRequest(
        AiMode generalMode,
        AiAssistantRequest assistant) {
}
