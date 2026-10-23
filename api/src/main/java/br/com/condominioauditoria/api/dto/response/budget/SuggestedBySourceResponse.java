package br.com.condominioauditoria.api.dto.response.budget;

/** Accounts SUGGESTED in the version, by where the suggestion came from (Q51, "Confirmar todas as sugestões"). */
public record SuggestedBySourceResponse(
        int previousVersion,
        int spreadsheet,
        int name,
        int admin) {
}
