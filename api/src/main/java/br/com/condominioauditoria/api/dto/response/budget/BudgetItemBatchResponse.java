package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Result of a budget item batch: how many lines changed and the ones skipped, with the reason. */
public record BudgetItemBatchResponse(
        @JsonProperty("alteradas") int changed,
        @JsonProperty("ignoradas") List<SkippedLineResponse> skipped) {
}
