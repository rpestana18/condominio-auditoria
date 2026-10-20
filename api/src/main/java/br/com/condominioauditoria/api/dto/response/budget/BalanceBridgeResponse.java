package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.BudgetVsActualStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Balance bridge (RF-13.6): items that add up exactly to the balance variation of the fund (or of all funds) between the opening balance of the period and the balance of the last complete month. {@code closes} false = the bridge does not close ("a ponte não fecha"), with the UNEXPLAINED item highlighted. */
public record BalanceBridgeResponse(
        String calculationVersion,
        BudgetVsActualStatus status,
        String message,
        UUID fundId,
        String fundName,
        boolean allFunds,
        String from,
        String to,
        BudgetBriefResponse budget,
        BigDecimal openingBalance,
        String openingSource,
        BigDecimal closingBalance,
        BigDecimal variation,
        Boolean closes,
        List<BridgeItemResponse> items,
        List<BridgeMonthResponse> months,
        List<BudgetVsActualWarningResponse> warnings) {
}
