package br.com.condominioauditoria.api.dto.request.ai;

import br.com.condominioauditoria.api.model.enums.AiMode;
import com.fasterxml.jackson.annotation.JsonProperty;

/** PUT /condominios/{id}/ia (contracts/openapi.yaml, PedidoConfiguracaoIa). */
public record AiConfigurationRequest(
        @JsonProperty("modoGeral") AiMode generalMode,
        @JsonProperty("assistente") AiAssistantRequest assistant) {
}
