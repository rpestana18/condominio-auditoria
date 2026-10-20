package br.com.condominioauditoria.api.dto.response.accounting;

import java.math.BigDecimal;

/** Movement of a fund in a month. Credits = condoFee + otherIncome + transfersIn + otherCredits; debits = expenses + transfersOut + otherDebits ("outros" = without a mark, never hidden). Opening and closing balances and printed totals as in the cash flow; {@code calculatedClosingBalance} = opening + credits − debits of the entries, checked against the printed one (tolerance 0,01). {@code planned}: monthly planned of lines 1.1 to 1.8 (operating fund) or of the linked 1.9 line; {@code collected}: fee received (1.9 funds). */
public record FundMonthMovementResponse(
        String month,
        boolean present,
        BigDecimal openingBalance,
        BigDecimal condoFee,
        BigDecimal otherIncome,
        BigDecimal transfersIn,
        BigDecimal otherCredits,
        BigDecimal credits,
        BigDecimal expenses,
        BigDecimal transfersOut,
        BigDecimal otherDebits,
        BigDecimal debits,
        BigDecimal closingBalance,
        BigDecimal printedCredits,
        BigDecimal printedDebits,
        BigDecimal calculatedClosingBalance,
        Boolean balanceMatches,
        BigDecimal planned,
        BigDecimal collected,
        int entries) {
}
