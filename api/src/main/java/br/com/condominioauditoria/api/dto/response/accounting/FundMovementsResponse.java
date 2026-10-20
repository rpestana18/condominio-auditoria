package br.com.condominioauditoria.api.dto.response.accounting;

import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualWarningResponse;
import java.util.List;
import java.util.UUID;

/** Every fund of the cash flows of the period, month by month (RF-13.5). Calculated on each request by FundMovements. */
public record FundMovementsResponse(
        String calculationVersion,
        String from,
        String to,
        UUID operatingFundId,
        List<FundMonthSummaryResponse> months,
        List<FundMovementResponse> funds,
        List<BudgetVsActualWarningResponse> warnings) {
}
