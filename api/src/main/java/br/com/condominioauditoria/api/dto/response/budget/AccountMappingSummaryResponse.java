package br.com.condominioauditoria.api.dto.response.budget;


/** Account counts of the mapping screen, by status. */
public record AccountMappingSummaryResponse(
        int accounts,
        int confirmed,
        int suggested,
        int rejected,
        int withoutMapping) {
}
