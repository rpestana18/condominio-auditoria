package br.com.condominioauditoria.api.dto.response.budget;

import java.math.BigDecimal;
import java.util.List;

/** Separate block, outside the lines (adjustments, to reallocate, without budget line). */
public record EntryBlockResponse(
        BigDecimal total,
        int entries,
        List<BlockAccountResponse> accounts) {
}
