package br.com.condominioauditoria.api.dto.response.budget;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@code targets}: the evidence target of the group in each fiscal year, in the order of {@code fiscalYears} (the same
 * as {@link ComparedValueResponse#target()}); null when the group does not exist in the fiscal year or the fiscal year
 * is the printed column.
 */
public record ComparisonGroupResponse(
        String code,
        String description,
        List<BigDecimal> monthlyPlanned,
        List<String> targets) {
}
