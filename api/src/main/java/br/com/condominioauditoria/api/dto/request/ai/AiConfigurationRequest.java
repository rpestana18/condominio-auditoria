package br.com.condominioauditoria.api.dto.request.ai;

import br.com.condominioauditoria.api.model.enums.AiMode;

/** PUT /condominios/{id}/ia (contracts/openapi.yaml, PedidoConfiguracaoIa). */
public record AiConfigurationRequest(
        AiMode generalMode,
        AiAssistantRequest assistant) {
}
