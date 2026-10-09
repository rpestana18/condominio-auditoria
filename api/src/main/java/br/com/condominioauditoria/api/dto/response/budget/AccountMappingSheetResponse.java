package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Result of the suggestion sheet upload: accepted, skipped (already confirmed) and rejected lines. */
public record AccountMappingSheetResponse(
        @JsonProperty("aceitas") int accepted,
        @JsonProperty("ignoradas") List<SkippedAccountResponse> skipped,
        @JsonProperty("recusadas") List<RejectedSheetLineResponse> rejected) {
}
