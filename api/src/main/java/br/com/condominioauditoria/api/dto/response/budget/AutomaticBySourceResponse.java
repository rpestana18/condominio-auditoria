package br.com.condominioauditoria.api.dto.response.budget;

/** Accounts linked by the system (AUTOMATIC) in the version, by where the link came from (Q54). */
public record AutomaticBySourceResponse(
        int previousVersion,
        int spreadsheet,
        int budgetSheet,
        int name,
        int admin) {
}
