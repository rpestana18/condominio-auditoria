package br.com.condominioauditoria.api.dto.response.accounting;

import br.com.condominioauditoria.api.model.enums.MonthStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A month of "Entradas e saídas" (RF-13.11). Without a cash flow ({@code status} NO_CASH_FLOW or TWO_CASH_FLOWS) every
 * number is null ("sem extrato carregado", never zero). {@code partialUntil}: month in progress, numbers up to that day
 * (RF-13.10). {@code beforeBudget}: month before the approved budget (RF-13.16), with no budget line and no planned
 * amount. {@code byAccount}: the outflows of the month come by cash flow account (before the budget, or lines not yet
 * reviewed). {@code monthBalance} = inflows − outflows + transfersIn − transfersOut; {@code closingBalance}: the printed
 * closing balance (the cumulative balance, N2). {@code debitsMatch}: outflows + transfersOut = debits of the cash flow,
 * to the cent (D6); {@code balanceMatches}: opening + credits − debits = printed closing balance.
 */
public record CashFlowMonthResponse(
        String month,
        MonthStatus status,
        LocalDate partialUntil,
        boolean beforeBudget,
        boolean byAccount,
        BigDecimal inflows,
        BigDecimal outflows,
        BigDecimal inLines,
        BigDecimal transfersIn,
        BigDecimal transfersOut,
        BigDecimal monthBalance,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        BigDecimal printedDebits,
        Boolean debitsMatch,
        Boolean balanceMatches) {
}
