package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.BudgetLineMark;
import br.com.condominioauditoria.api.model.enums.SpendingProfile;
import br.com.condominioauditoria.api.model.enums.SpendingProfileStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Budget line in the grid, identified by its id (ADR 0007, Decision 1). {@code code}: display label (the printed code when there is none). {@code balance}: cumulative planned − cumulative actual, only in the complete months with a cash flow (positive = left over). {@code effectiveProfile}: the confirmed profile, or MONTHLY without confirmation. */
public record TrackingLineResponse(
        UUID lineId,
        int position,
        String code,
        String printedCode,
        String description,
        String account,
        BudgetLineMark mark,
        String notes,
        int page,
        SpendingProfile spendingProfile,
        SpendingProfileStatus spendingProfileStatus,
        SpendingProfile effectiveProfile,
        BigDecimal monthlyPlanned,
        List<String> cashFlowAccounts,
        List<TrackingCellResponse> cells,
        BigDecimal cumulativePlanned,
        BigDecimal cumulativeActual,
        BigDecimal balance,
        int monthsAbove) {
}
