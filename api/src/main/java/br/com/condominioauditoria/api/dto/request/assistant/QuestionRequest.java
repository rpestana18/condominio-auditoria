package br.com.condominioauditoria.api.dto.request.assistant;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** A question to the Assistant chat (contracts/openapi.yaml, PedidoPergunta). */
public record QuestionRequest(
        @JsonProperty("question") String question,
        @JsonProperty("historico") List<ConversationTurnRequest> history,
        @JsonProperty("filtros") DocumentFiltersRequest filters) {
}
