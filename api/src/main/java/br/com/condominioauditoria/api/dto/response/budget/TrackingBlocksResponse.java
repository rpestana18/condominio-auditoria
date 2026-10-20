package br.com.condominioauditoria.api.dto.response.budget;


/** Blocks shown apart from the lines (RF-13.4): adjustments, to reallocate, without budget line and the separate apportionment lines (movement only, no difference, Q45). */
public record TrackingBlocksResponse(
        TrackingBlockResponse adjustments,
        TrackingBlockResponse toReallocate,
        TrackingBlockResponse withoutBudgetLine,
        TrackingBlockResponse separateApportionment) {
}
