package br.com.condominioauditoria.api.dto.request.budget;

/**
 * "Confirmar todas as sugestões" (Q51).
 *
 * @param expected how many SUGGESTED accounts the screen showed; a different count confirms nothing
 */
public record ConfirmSuggestedRequest(Integer expected) {
}
