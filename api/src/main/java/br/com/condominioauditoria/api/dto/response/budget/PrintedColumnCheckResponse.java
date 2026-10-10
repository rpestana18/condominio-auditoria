package br.com.condominioauditoria.api.dto.response.budget;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Check of a budget's "Orçado anterior" column (RF-11.5). {@code superseded}: there is a confirmed previous budget,
 * and the comparison uses it; {@code differences}: groups where the previous budget and the column differ beyond the
 * tolerance.
 */
public record PrintedColumnCheckResponse(
        String id,
        String label,
        UUID budgetId,
        boolean superseded,
        UUID previousBudgetId,
        String previousBudgetLabel,
        BigDecimal printedTotal,
        boolean totalIncludesFunds,
        BigDecimal funds,
        BigDecimal monthlyPlanned,
        List<PrintedColumnGroupResponse> groups,
        List<GroupDifferenceResponse> differences,
        List<String> warnings) {
}
