package br.com.condominioauditoria.rag.service.calculator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import br.com.condominioauditoria.rag.model.TotalsCheck;
import br.com.condominioauditoria.rag.model.cashflow.CashFlow;
import br.com.condominioauditoria.rag.model.cashflow.FundPosition;
import br.com.condominioauditoria.rag.model.cashflow.FundSection;
import br.com.condominioauditoria.rag.model.cashflow.LedgerEntry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

public class CashFlowCheckTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 1);

    private static LedgerEntry entry(String credit, String debit, String balance) {
        return new LedgerEntry(1, 1, DAY, "1062", "MATERIAL DE LIMPEZA", "1", "COMPRA",
                new BigDecimal(credit), new BigDecimal(debit), new BigDecimal(balance),
                new LedgerEntry.Enrichment(null, null, null, false, false));
    }

    private static CashFlow cashFlow(String printedBalance) {
        var section = new FundSection("CONDOMÍNIO", new BigDecimal("100.00"),
                List.of(entry("50.00", "0.00", "150.00"), entry("0.00", "30.00", printedBalance)),
                new BigDecimal("50.00"), new BigDecimal("30.00"));
        var position = new FundPosition("CONDOMÍNIO", new BigDecimal("100.00"), new BigDecimal("50.00"),
                new BigDecimal("30.00"), new BigDecimal("120.00"));
        return new CashFlow("TESTE", DAY, DAY.plusDays(29), List.of(section), List.of(position), position);
    }

    @Test
    public void consistentReportPassesEverything() {
        assertThat(CashFlowCheck.check(cashFlow("120.00"))).allMatch(TotalsCheck::ok);
    }

    @Test
    public void wrongPrintedBalanceIsPointedOutWithItsLine() {
        var result = CashFlowCheck.check(cashFlow("125.00"));

        TotalsCheck balance = result.getFirst();
        assertThat(balance.ok()).isFalse();
        assertThat(balance.detail()).contains("CONDOMÍNIO, pág. 1").contains("calculado 120,00, impresso 125,00");
    }

    private static FundSection fund(String name, String opening, List<LedgerEntry> entries, String credits,
            String debits) {
        return new FundSection(name, new BigDecimal(opening), entries, new BigDecimal(credits), new BigDecimal(debits));
    }

    private static CashFlow withSecondFund(FundSection second) {
        CashFlow base = cashFlow("120.00");
        return new CashFlow("TESTE", DAY, DAY.plusDays(29), List.of(base.sections().getFirst(), second),
                base.financialPosition(), base.positionTotal());
    }

    private static TotalsCheck closingBalance(CashFlow cashFlow) {
        return CashFlowCheck.check(cashFlow).stream().filter(c -> c.code().equals("FUND_CLOSING_BALANCE"))
                .findFirst().orElseThrow();
    }

    /** D2: a fund where nothing happened (0,00, no entry, TOTAIS 0,00) may be missing from the Posição, with a notice. */
    @Test
    public void emptyZeroFundOutsidePositionPassesWithNotice() {
        CashFlow cashFlow = withSecondFund(fund("OBRAS", "0.00", List.of(), "0.00", "0.00"));

        TotalsCheck check = closingBalance(cashFlow);

        assertThat(check.ok()).isTrue();
        assertThat(check.detail()).isEqualTo("2 fundos conferidos. Aviso: OBRAS não aparece na Posição Financeira; "
                + "sem lançamentos e com saldo anterior 0,00: o saldo final foi considerado 0,00");
        assertThat(ZeroFundPositions.complete(cashFlow).financialPosition())
                .extracting(FundPosition::fund, FundPosition::closingBalance)
                .containsExactly(tuple("CONDOMÍNIO", new BigDecimal("120.00")),
                        tuple("OBRAS", new BigDecimal("0.00")));
        assertThat(CashFlowCheck.check(cashFlow)).allMatch(TotalsCheck::ok);
    }

    /** Entries that cancel out (credit 110,39 and its reversal −110,39, TOTAIS 0,00 / 0,00): passes with a notice. */
    @Test
    public void entriesThatCancelOutOutsidePositionPassWithNotice() {
        CashFlow cashFlow = withSecondFund(fund("OBRAS", "0.00",
                List.of(entry("110.39", "0.00", "110.39"), entry("-110.39", "0.00", "0.00")), "0.00", "0.00"));

        TotalsCheck check = closingBalance(cashFlow);

        assertThat(check.ok()).isTrue();
        assertThat(check.detail()).isEqualTo("2 fundos conferidos. Aviso: OBRAS não aparece na Posição Financeira; "
                + "saldo anterior 0,00, entradas e saídas 0,00 e último saldo 0,00: o saldo final foi considerado 0,00");
        assertThat(ZeroFundPositions.complete(cashFlow).financialPosition()).extracting(FundPosition::fund)
                .containsExactly("CONDOMÍNIO", "OBRAS");
        assertThat(CashFlowCheck.check(cashFlow)).allMatch(TotalsCheck::ok);
    }

    /** TOTAIS 0,00 / 0,00 but a last printed balance other than 0,00: not certain, stays an error. */
    @Test
    public void fundWithNonZeroLastBalanceOutsidePositionIsStillAnError() {
        CashFlow cashFlow = withSecondFund(fund("OBRAS", "0.00",
                List.of(entry("110.39", "0.00", "110.39"), entry("-110.39", "0.00", "5.00")), "0.00", "0.00"));

        assertThat(closingBalance(cashFlow).ok()).isFalse();
        assertThat(ZeroFundPositions.missingFunds(cashFlow)).isEmpty();
    }

    /**
     * Credit and debit of the same amount end at 0,00, but TOTAIS 110,39 / 110,39 is not zero: the fund moved money,
     * so its absence from the Posição stays an error.
     */
    @Test
    public void fundWithNonZeroTotalsOutsidePositionIsStillAnError() {
        CashFlow cashFlow = withSecondFund(fund("OBRAS", "0.00",
                List.of(entry("110.39", "0.00", "110.39"), entry("0.00", "110.39", "0.00")), "110.39", "110.39"));

        TotalsCheck check = closingBalance(cashFlow);

        assertThat(check.ok()).isFalse();
        assertThat(check.detail()).isEqualTo("OBRAS: não aparece na Posição Financeira");
        assertThat(ZeroFundPositions.missingFunds(cashFlow)).isEmpty();
        assertThat(ZeroFundPositions.complete(cashFlow)).isSameAs(cashFlow);
    }

    /** Without entries, but with an opening balance (or TOTAIS) other than 0,00: the closing balance is not certain. */
    @Test
    public void fundWithBalanceOutsidePositionIsStillAnError() {
        assertThat(closingBalance(withSecondFund(fund("OBRAS", "10.00", List.of(), "0.00", "0.00"))).ok()).isFalse();
        assertThat(closingBalance(withSecondFund(fund("OBRAS", "0.00", List.of(), "5.00", "0.00"))).ok()).isFalse();
        assertThat(closingBalance(withSecondFund(fund("OBRAS", "0.00", List.of(), "0.00", "5.00"))).ok()).isFalse();
    }
}
