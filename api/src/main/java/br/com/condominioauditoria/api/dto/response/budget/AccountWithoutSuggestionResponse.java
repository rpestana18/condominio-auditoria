package br.com.condominioauditoria.api.dto.response.budget;


/** Cash flow account left without a suggestion, with the reason. */
public record AccountWithoutSuggestionResponse(
        String account,
        String name,
        String reason) {
}
