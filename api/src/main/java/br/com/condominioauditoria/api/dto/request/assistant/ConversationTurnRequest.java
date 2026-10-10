package br.com.condominioauditoria.api.dto.request.assistant;


/** One previous question and answer of the conversation. */
public record ConversationTurnRequest(
        String question,
        String answer) {
}
