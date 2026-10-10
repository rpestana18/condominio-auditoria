package br.com.condominioauditoria.api.dto.response.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DashboardResponse(
        UUID fileId,
        String fileName,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal openingBalance,
        BigDecimal inflows,
        BigDecimal outflows,
        BigDecimal closingBalance,
        long failedChecks,
        OperatingFundResponse operatingFund,
        List<FundPeriodResponse> funds,
        List<ExpenseResponse> largestExpenses) {
}
