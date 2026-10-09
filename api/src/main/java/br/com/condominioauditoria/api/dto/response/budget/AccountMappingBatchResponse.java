package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Result of a mapping batch: how many accounts changed and the ones skipped, with the reason. */
public record AccountMappingBatchResponse(
        @JsonProperty("alteradas") int changed,
        @JsonProperty("ignoradas") List<SkippedAccountResponse> skipped) {
}
