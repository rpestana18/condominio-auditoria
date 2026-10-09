package br.com.condominioauditoria.api.dto.request.assistant;

import com.fasterxml.jackson.annotation.JsonProperty;

/** One previous question and answer of the conversation. */
public record ConversationTurnRequest(
        @JsonProperty("pergunta") String question,
        @JsonProperty("resposta") String answer) {
}
