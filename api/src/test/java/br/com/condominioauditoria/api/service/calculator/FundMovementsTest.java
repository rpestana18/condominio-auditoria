package br.com.condominioauditoria.api.service.calculator;

import static br.com.condominioauditoria.api.service.calculator.SyntheticSeptember.OPERATING;
import static br.com.condominioauditoria.api.service.calculator.SyntheticSeptember.RESERVE;
import static br.com.condominioauditoria.api.service.calculator.SyntheticSeptember.SEPTEMBER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.api.dto.response.accounting.FundMonthMovementResponse;
import br.com.condominioauditoria.api.dto.response.accounting.FundMovementsResponse;
import br.com.condominioauditoria.api.model.accounting.LedgerEntry;
import br.com.condominioauditoria.api.model.enums.MonthStatus;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** RF-13.5: every fund of the cash flow, month by month, with credits and debits split by the existing marks. */
class FundMovementsTest {

    private final SyntheticSeptember s = new SyntheticSeptember();

    @Test
    void septemberFundsWithPrintedBalancesAndSplit() {
        FundMovementsResponse r = FundMovements.calculate(s.funds(YearMonth.of(2026, 5), YearMonth.of(2027, 4)));

        assertThat(r.months()).hasSize(12);
        assertThat(r.months()).filteredOn(m -> m.month().equals("2026-09")).singleElement().satisfies(m -> {
            assertThat(m.status()).isEqualTo(MonthStatus.WITH_CASH_FLOW);
            assertThat(m.funds()).isEqualTo(2);
            assertThat(m.totalClosingBalance()).isEqualByComparingTo("317575.27");
        });
        assertThat(r.months()).filteredOn(m -> !m.month().equals("2026-09"))
                .allSatisfy(m -> assertThat(m.totalClosingBalance()).isNull());
        // Operating fund first
        assertThat(r.funds()).extracting(f -> f.name()).containsExactly("CONDOMÍNIO", "FUNDO DE RESERVA");

        FundMonthMovementResponse op = month(r, OPERATING, "2026-09");
        assertThat(op.openingBalance()).isEqualByComparingTo("310735.05");
        assertThat(op.condoFee()).isEqualByComparingTo("440000.00");
        assertThat(op.otherIncome()).isEqualByComparingTo("1234.56");
        assertThat(op.transfersIn()).isEqualByComparingTo("0.00");
        assertThat(op.otherCredits()).isEqualByComparingTo("0.00");
        assertThat(op.credits()).isEqualByComparingTo("441234.56");
        // Expenses = lines + to reallocate + without budget line; adjustments are "outros débitos"
        assertThat(op.expenses()).isEqualByComparingTo("446176.89");
        assertThat(op.transfersOut()).isEqualByComparingTo("5000.00");
        assertThat(op.otherDebits()).isEqualByComparingTo("3278.24");
        assertThat(op.debits()).isEqualByComparingTo("454455.13");
        assertThat(op.closingBalance()).isEqualByComparingTo("297514.48");
        assertThat(op.calculatedClosingBalance()).isEqualByComparingTo("297514.48");
        assertThat(op.balanceMatches()).isTrue();
        assertThat(op.planned()).isEqualByComparingTo("451620.13");
        assertThat(op.collected()).isNull();

        FundMonthMovementResponse reserve = month(r, RESERVE, "2026-09");
        assertThat(reserve.condoFee()).isEqualByComparingTo("14260.79");
        assertThat(reserve.collected()).isEqualByComparingTo("14260.79");
        assertThat(reserve.planned()).isEqualByComparingTo("13548.60");
        assertThat(reserve.transfersIn()).isEqualByComparingTo("5000.00");
        assertThat(reserve.expenses()).isEqualByComparingTo("200.00");
        assertThat(reserve.balanceMatches()).isTrue();
        assertThat(r.funds().get(1).budgetLineCode()).isEqualTo("1.9.1");
        assertThat(month(r, OPERATING, "2026-08").present()).isFalse();
        assertThat(month(r, OPERATING, "2026-08").closingBalance()).isNull();
        assertThat(r.warnings()).isEmpty();
    }

    @Test
    void calculatedBalanceDifferentFromThePrintedOneWarns() {
        s.balances.removeIf(b -> b.getFundId().equals(RESERVE));
        s.balances.add(SyntheticSeptember.balance(s.septemberFile, SEPTEMBER, RESERVE, "FUNDO DE RESERVA", "1000.00",
                "19260.79", "200.00", "20060.81"));

        FundMovementsResponse r = FundMovements.calculate(s.funds(SEPTEMBER, SEPTEMBER));

        assertThat(month(r, RESERVE, "2026-09").balanceMatches()).isFalse();
        assertThat(r.warnings()).singleElement().satisfies(w -> {
            assertThat(w.code()).isEqualTo("FUND_BALANCE_MISMATCH");
            assertThat(w.text()).isEqualTo("Fundo FUNDO DE RESERVA, 09/2026: saldo calculado difere do extrato"
                    + " (calculado R$ 20.060,79; extrato R$ 20.060,81)");
        });
    }

    @Test
    void oneCentIsWithinTheTolerance() {
        s.balances.removeIf(b -> b.getFundId().equals(RESERVE));
        s.balances.add(SyntheticSeptember.balance(s.septemberFile, SEPTEMBER, RESERVE, "FUNDO DE RESERVA", "1000.00",
                "19260.79", "200.00", "20060.80"));

        assertThat(month(FundMovements.calculate(s.funds(SEPTEMBER, SEPTEMBER)), RESERVE, "2026-09").balanceMatches())
                .isTrue();
    }

    @Test
    void creditWithoutTheFeeMarkIsOtherCreditsNeverHidden() {
        LedgerEntry old = mock(LedgerEntry.class);
        when(old.getId()).thenReturn(UUID.randomUUID());
        when(old.getFileId()).thenReturn(s.septemberFile);
        when(old.getFundId()).thenReturn(RESERVE);
        when(old.getDate()).thenReturn(SEPTEMBER.atDay(20));
        when(old.getCredit()).thenReturn(new BigDecimal("75.00"));
        when(old.getDebit()).thenReturn(BigDecimal.ZERO.setScale(2));
        when(old.getCondoFeeReceipt()).thenReturn(null);
        s.entries.add(old);

        FundMovementsResponse r = FundMovements.calculate(s.funds(SEPTEMBER, SEPTEMBER));

        FundMonthMovementResponse reserve = month(r, RESERVE, "2026-09");
        assertThat(reserve.otherCredits()).isEqualByComparingTo("75.00");
        assertThat(reserve.credits()).isEqualByComparingTo("19335.79");
        assertThat(r.warnings()).anySatisfy(w -> assertThat(w.code()).isEqualTo("UNCLASSIFIED_CREDITS"));
    }

    static FundMonthMovementResponse month(FundMovementsResponse r, UUID fund, String month) {
        return r.funds().stream().filter(f -> f.fundId().equals(fund)).findFirst().orElseThrow().months().stream()
                .filter(m -> m.month().equals(month)).findFirst().orElseThrow();
    }
}
