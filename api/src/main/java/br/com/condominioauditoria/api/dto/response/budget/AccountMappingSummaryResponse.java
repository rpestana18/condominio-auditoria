package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Account counts of the mapping screen, by status. */
public record AccountMappingSummaryResponse(
        @JsonProperty("contas") int accounts,
        @JsonProperty("confirmadas") int confirmed,
        @JsonProperty("sugeridas") int suggested,
        @JsonProperty("recusadas") int rejected,
        @JsonProperty("semDepara") int withoutMapping) {
}
