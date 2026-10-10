package br.com.condominioauditoria.api.dto.response.ai;


/** The Assistant's AI configuration: answers and embeddings. */
public record AiAssistantResponse(
        AiAnswersResponse answers,
        AiEmbeddingsResponse embeddings) {
}
