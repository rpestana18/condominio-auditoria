package br.com.condominioauditoria.api.dto.response.dashboard;

import br.com.condominioauditoria.api.model.enums.MonthlyPointStatus;
import java.math.BigDecimal;
import java.util.List;

/**
 * One month of a series (RF-12.2 and RF-12.3). {@code amount} only when the status is {@code LOADED}: a month without
 * a cash flow is never zero. Each variation is null when one of the two months has no amount ("sem base de
 * comparação").
 */
public record MonthlyPointResponse(
        String month,
        MonthlyPointStatus status,
        BigDecimal amount,
        MonthlyVariationResponse previousMonth,
        MonthlyVariationResponse previousYear,
        List<MonthlyEntryResponse> entries) {
}
