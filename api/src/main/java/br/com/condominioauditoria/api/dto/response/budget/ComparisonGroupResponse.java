package br.com.condominioauditoria.api.dto.response.budget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

/**
 * {@code targets}: the evidence target of the group in each fiscal year, in the order of {@code fiscalYears} (the same
 * as {@link ComparedValueResponse#target()}); null when the group does not exist in the fiscal year or the fiscal year
 * is the printed column.
 */
public record ComparisonGroupResponse(
        @JsonProperty("codigo") String code,
        @JsonProperty("descricao") String description,
        @JsonProperty("previstoMes") List<BigDecimal> monthlyPlanned,
        @JsonProperty("alvos") List<String> targets) {
}
