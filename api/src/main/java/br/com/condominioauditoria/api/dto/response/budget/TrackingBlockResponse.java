package br.com.condominioauditoria.api.dto.response.budget;

import java.math.BigDecimal;
import java.util.List;

/** Separate block of the grid, month by month, with the cumulative of the complete months. */
public record TrackingBlockResponse(
        List<TrackingBlockCellResponse> cells,
        BigDecimal cumulative,
        int cumulativeEntries) {
}
