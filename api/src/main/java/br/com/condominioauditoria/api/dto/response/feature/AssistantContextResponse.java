package br.com.condominioauditoria.api.dto.response.feature;

import br.com.condominioauditoria.api.model.enums.AiMode;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Effective Assistant mode for the screen (RF-04.16); null with the feature disabled. Never carries a key. */
public record AssistantContextResponse(
        @JsonProperty("modoRespostas") AiMode answersMode,
        @JsonProperty("modoEmbeddings") AiMode embeddingsMode,
        @JsonProperty("chatDisponivel") boolean chatAvailable) {
}
