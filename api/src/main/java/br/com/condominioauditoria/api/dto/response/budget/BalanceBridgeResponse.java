package br.com.condominioauditoria.api.dto.response.budget;

import br.com.condominioauditoria.api.model.enums.BudgetVsActualStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Balance bridge (RF-13.6): items that add up exactly to the balance variation of the fund (or of all funds) between
 * the opening balance of the period and the balance of the last complete month. {@code closes} false = the bridge does
 * not close ("a ponte não fecha"), with the UNEXPLAINED item highlighted.
 * <p>
 * {@code from}/{@code to} are the requested period (by default the whole fiscal year). The total covers only
 * {@code effectiveFrom} to {@code effectiveTo}: from the opening balance at {@code openingDate} to the closing balance
 * at {@code closingDate} (last day of the last complete month). A partial month comes in {@code partialMonth}, outside
 * the total. {@code mapping}: account mapping state of the operating fund in the total; null when the bridge does not
 * include the operating fund or has no numbers. {@code lineReview}: review of the budget lines (RF-13.12); while they are
 * not reviewed, the items by line (1 to 3 and the separate apportionment) have a null amount, and the pending step comes
 * here. Null when the bridge has no budget.
 */
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
        List<BudgetVsActualWarningResponse> warnings,
        String effectiveFrom,
        String effectiveTo,
        LocalDate openingDate,
        LocalDate closingDate,
        BridgePartialMonthResponse partialMonth,
        BridgeMappingResponse mapping,
        LineReviewStateResponse lineReview) {

    /** The same bridge with other items and months and the review state. */
    public BalanceBridgeResponse withLineReview(List<BridgeItemResponse> newItems, List<BridgeMonthResponse> newMonths,
            LineReviewStateResponse review) {
        return new BalanceBridgeResponse(calculationVersion, status, message, fundId, fundName, allFunds, from, to,
                budget, openingBalance, openingSource, closingBalance, variation, closes, newItems, newMonths, warnings,
                effectiveFrom, effectiveTo, openingDate, closingDate, partialMonth, mapping, review);
    }
}
