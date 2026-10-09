package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

/** Separate block, outside the lines (adjustments, to reallocate, without budget line). */
public record EntryBlockResponse(
        BigDecimal total,
        @JsonProperty("lancamentos") int entries,
        @JsonProperty("contas") List<BlockAccountResponse> accounts) {
}
