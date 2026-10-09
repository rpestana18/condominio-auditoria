package br.com.condominioauditoria.api.dto.response.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

/** The Assistant's AI configuration: answers and embeddings. */
public record AiAssistantResponse(
        @JsonProperty("respostas") AiAnswersResponse answers,
        AiEmbeddingsResponse embeddings) {
}
