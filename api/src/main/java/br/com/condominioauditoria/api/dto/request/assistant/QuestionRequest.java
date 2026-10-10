package br.com.condominioauditoria.api.dto.request.assistant;

import java.util.List;

/** A question to the Assistant chat (contracts/openapi.yaml, PedidoPergunta). */
public record QuestionRequest(
        String question,
        List<ConversationTurnRequest> history,
        DocumentFiltersRequest filters) {
}
