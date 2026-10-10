package br.com.condominioauditoria.api.dto.response.budget;

import java.util.List;

/** Result of a budget item batch: how many lines changed and the ones skipped, with the reason. */
public record BudgetItemBatchResponse(
        int changed,
        List<SkippedLineResponse> skipped) {
}
