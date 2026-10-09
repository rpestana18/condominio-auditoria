package br.com.condominioauditoria.api.dto.request.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

/** The Assistant's part of the request: answers and embeddings. */
public record AiAssistantRequest(
        @JsonProperty("respostas") AiAnswersRequest answers,
        AiEmbeddingsRequest embeddings) {
}
