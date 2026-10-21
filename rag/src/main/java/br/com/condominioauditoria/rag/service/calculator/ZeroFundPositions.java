package br.com.condominioauditoria.rag.service.calculator;

import br.com.condominioauditoria.rag.model.cashflow.CashFlow;
import br.com.condominioauditoria.rag.model.cashflow.FundPosition;
import br.com.condominioauditoria.rag.model.cashflow.FundSection;
import br.com.condominioauditoria.rag.util.BrazilianMoney;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Funds that the management company leaves out of the Posição Financeira because they end where they started, at
 * zero: SALDO ANTERIOR 0,00, TOTAIS 0,00 / 0,00 and the last printed balance 0,00 (no entry, or entries that cancel out,
 * as a credit and its reversal). Only for these the closing balance 0,00 is certain without the printed line, so only
 * these get a Posição line 0,00 (with a notice in the FUND_CLOSING_BALANCE check). Any other fund missing from the
 * Posição stays an error to review.
 */
public final class ZeroFundPositions {

    private ZeroFundPositions() {
    }

    /** Sections that may get a closing balance 0,00 without being in the Posição Financeira. */
    public static List<FundSection> missingFunds(CashFlow cashFlow) {
        Set<String> listed = cashFlow.financialPosition().stream().map(FundPosition::fund).collect(Collectors.toSet());
        return cashFlow.sections().stream()
                .filter(s -> !listed.contains(s.fund()))
                .filter(ZeroFundPositions::endsAtZero)
                .toList();
    }

    /** The same cash flow, with a Posição line 0,00 for each fund found by {@link #missingFunds}. */
    public static CashFlow complete(CashFlow cashFlow) {
        List<FundSection> missing = missingFunds(cashFlow);
        if (missing.isEmpty()) {
            return cashFlow;
        }
        List<FundPosition> position = new ArrayList<>(cashFlow.financialPosition());
        BigDecimal zero = BrazilianMoney.ZERO;
        missing.forEach(s -> position.add(new FundPosition(s.fund(), zero, zero, zero, zero)));
        return new CashFlow(cashFlow.property(), cashFlow.periodStart(), cashFlow.periodEnd(), cashFlow.sections(),
                List.copyOf(position), cashFlow.positionTotal());
    }

    private static boolean endsAtZero(FundSection section) {
        BigDecimal lastBalance = section.entries().isEmpty() ? section.openingBalance()
                : section.entries().getLast().balance();
        return section.openingBalance().signum() == 0
                && lastBalance.signum() == 0
                && section.reportedCreditTotal().signum() == 0
                && section.reportedDebitTotal().signum() == 0;
    }
}
