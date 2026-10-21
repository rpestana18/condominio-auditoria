package br.com.condominioauditoria.api.dto.response.budget;

import java.math.BigDecimal;
import java.util.List;

/**
 * Separate block of the grid, month by month, with the cumulative of the complete months. {@code accounts}: the
 * accounts of the block merged over the same complete months (largest amount first); empty in the separate
 * apportionment block, which is made of budget lines.
 */
public record TrackingBlockResponse(
        List<TrackingBlockCellResponse> cells,
        BigDecimal cumulative,
        int cumulativeEntries,
        List<BlockAccountResponse> accounts) {
}
