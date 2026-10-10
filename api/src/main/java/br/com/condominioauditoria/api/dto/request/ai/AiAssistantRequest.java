package br.com.condominioauditoria.api.dto.request.ai;


/** The Assistant's part of the request: answers and embeddings. */
public record AiAssistantRequest(
        AiAnswersRequest answers,
        AiEmbeddingsRequest embeddings) {
}
