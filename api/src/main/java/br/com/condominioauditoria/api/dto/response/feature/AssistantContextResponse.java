package br.com.condominioauditoria.api.dto.response.feature;

import br.com.condominioauditoria.api.model.enums.AiMode;

/** Effective Assistant mode for the screen (RF-04.16); null with the feature disabled. Never carries a key. */
public record AssistantContextResponse(
        AiMode answersMode,
        AiMode embeddingsMode,
        boolean chatAvailable) {
}
