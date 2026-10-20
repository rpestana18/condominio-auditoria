package br.com.condominioauditoria.api.dto.response.accounting;

import br.com.condominioauditoria.api.dto.response.budget.UsedCashFlowResponse;
import br.com.condominioauditoria.api.model.enums.MonthStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** A month of the funds screen: status, partial mark, the cash flow used and the sum of the closing balances of every fund printed in it (null without numbers). */
public record FundMonthSummaryResponse(
        String month,
        MonthStatus status,
        LocalDate partialUntil,
        List<UsedCashFlowResponse> cashFlows,
        int funds,
        BigDecimal totalClosingBalance) {
}
