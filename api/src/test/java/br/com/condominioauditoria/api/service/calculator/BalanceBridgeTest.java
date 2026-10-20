package br.com.condominioauditoria.api.service.calculator;

import static br.com.condominioauditoria.api.service.calculator.SyntheticSeptember.OCTOBER;
import static br.com.condominioauditoria.api.service.calculator.SyntheticSeptember.OPERATING;
import static br.com.condominioauditoria.api.service.calculator.SyntheticSeptember.RESERVE;
import static br.com.condominioauditoria.api.service.calculator.SyntheticSeptember.SEPTEMBER;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.api.dto.response.budget.BalanceBridgeResponse;
import br.com.condominioauditoria.api.dto.response.budget.BridgeItemResponse;
import br.com.condominioauditoria.api.dto.response.budget.BridgeLineResponse;
import br.com.condominioauditoria.api.model.enums.BalanceBridgeItemCode;
import br.com.condominioauditoria.api.model.enums.BudgetVsActualStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** RF-13.6: the balance bridge of September/2026 (synthetic, with the numbers of the requirement). */
class BalanceBridgeTest {

    private final SyntheticSeptember s = new SyntheticSeptember();

    @Test
    void operatingFundSeptember() {
        BalanceBridgeResponse r = bridge(OPERATING, SEPTEMBER, SEPTEMBER);

        assertThat(r.status()).isEqualTo(BudgetVsActualStatus.CALCULATED);
        assertThat(r.fundName()).isEqualTo("CONDOMÍNIO");
        assertThat(r.openingBalance()).isEqualByComparingTo("310735.05");
        assertThat(r.openingSource()).isEqualTo("saldo inicial impresso no extrato de 09/2026");
        assertThat(r.closingBalance()).isEqualByComparingTo("297514.48");
        assertThat(r.variation()).isEqualByComparingTo("-13220.57");
        assertThat(item(r, BalanceBridgeItemCode.PROVISIONS_RESERVED).amount()).isEqualByComparingTo("2177.25");
        assertThat(item(r, BalanceBridgeItemCode.PROVISIONS_RESERVED).lines())
                .extracting(l -> l.code() + "=" + l.amount().toPlainString())
                .containsExactly("1.1.12=2857.95", "1.1.5=1585.14", "1.1.6=-2265.84");
        assertThat(item(r, BalanceBridgeItemCode.LINE_SAVINGS).amount()).isEqualByComparingTo("4316.92");
        assertThat(item(r, BalanceBridgeItemCode.SEPARATE_APPORTIONMENT).amount()).isEqualByComparingTo("0.00");
        BridgeItemResponse outside = item(r, BalanceBridgeItemCode.OUTSIDE_BUDGET);
        assertThat(outside.parts()).extracting(p -> p.code() + "=" + p.amount().toPlainString())
                .containsExactly("TO_REALLOCATE=-1050.93", "WITHOUT_BUDGET_LINE=0.00", "ADJUSTMENTS=-3278.24");
        BridgeItemResponse income = item(r, BalanceBridgeItemCode.INCOME_VS_PLANNED);
        assertThat(income.amount()).isEqualByComparingTo("-10385.57");
        assertThat(income.parts()).extracting(p -> p.code() + "=" + p.amount().toPlainString())
                .containsExactly("CONDO_FEE=440000.00", "OTHER_INCOME=1234.56", "PLANNED=-451620.13");
        assertThat(item(r, BalanceBridgeItemCode.INTER_FUND_TRANSFERS).amount()).isEqualByComparingTo("-5000.00");
        assertThat(item(r, BalanceBridgeItemCode.UNEXPLAINED).amount()).isEqualByComparingTo("0.00");
        assertThat(r.closes()).isTrue();
        // The items add up exactly to the variation, cent by cent
        assertThat(r.items().stream().map(BridgeItemResponse::amount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(r.variation());
        assertThat(r.items()).extracting(BridgeItemResponse::code).doesNotContain(
                BalanceBridgeItemCode.FUND_PLANNED_COLLECTION, BalanceBridgeItemCode.FUND_EXPENSES);
        assertThat(item(r, BalanceBridgeItemCode.LINE_SAVINGS).percentageOfVariation()).isEqualByComparingTo("-32.7");
        assertThat(r.warnings()).isEmpty();
    }

    @Test
    void savingsListShowsTheLinesThatContributedMost() {
        BalanceBridgeResponse r = bridge(OPERATING, SEPTEMBER, SEPTEMBER);

        var lines = item(r, BalanceBridgeItemCode.LINE_SAVINGS).lines();
        assertThat(lines).extracting(BridgeLineResponse::code).startsWith("1.8.1", "1.1.1");
        assertThat(lines).filteredOn(l -> l.code().equals("1.1.1")).singleElement()
                .satisfies(l -> assertThat(l.amount()).isEqualByComparingTo("9556.62"));
        assertThat(lines.getLast().code()).isEqualTo("1.3.10");
        assertThat(lines.getLast().amount()).isEqualByComparingTo("-6793.38");
    }

    @Test
    void allFundsCloseToo() {
        BalanceBridgeResponse r = bridge(null, SEPTEMBER, SEPTEMBER);

        assertThat(r.allFunds()).isTrue();
        assertThat(r.openingBalance()).isEqualByComparingTo("311735.05");
        assertThat(r.closingBalance()).isEqualByComparingTo("317575.27");
        assertThat(r.variation()).isEqualByComparingTo("5840.22");
        assertThat(item(r, BalanceBridgeItemCode.INCOME_VS_PLANNED).amount()).isEqualByComparingTo("-9673.38");
        assertThat(item(r, BalanceBridgeItemCode.FUND_PLANNED_COLLECTION).amount()).isEqualByComparingTo("13548.60");
        assertThat(item(r, BalanceBridgeItemCode.FUND_EXPENSES).amount()).isEqualByComparingTo("-200.00");
        assertThat(item(r, BalanceBridgeItemCode.INTER_FUND_TRANSFERS).amount()).isEqualByComparingTo("0.00");
        assertThat(item(r, BalanceBridgeItemCode.UNEXPLAINED).amount()).isEqualByComparingTo("0.00");
        assertThat(r.closes()).isTrue();
    }

    @Test
    void reserveFundAlone() {
        BalanceBridgeResponse r = bridge(RESERVE, SEPTEMBER, SEPTEMBER);

        assertThat(r.variation()).isEqualByComparingTo("19060.79");
        assertThat(item(r, BalanceBridgeItemCode.INCOME_VS_PLANNED).amount()).isEqualByComparingTo("712.19");
        assertThat(r.items()).extracting(BridgeItemResponse::code).doesNotContain(BalanceBridgeItemCode.LINE_SAVINGS);
        assertThat(item(r, BalanceBridgeItemCode.UNEXPLAINED).amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void anEntryOutsideEveryItemShowsAsUnexplained() {
        // The printed closing balance has 150,00 more than the entries read: no item explains it
        s.balances.removeIf(b -> b.getFundId().equals(OPERATING));
        s.balances.add(SyntheticSeptember.balance(s.septemberFile, SEPTEMBER, OPERATING, "CONDOMÍNIO", "310735.05",
                "441384.56", "454455.13", "297664.48"));

        BalanceBridgeResponse r = bridge(OPERATING, SEPTEMBER, SEPTEMBER);

        assertThat(item(r, BalanceBridgeItemCode.UNEXPLAINED).amount()).isEqualByComparingTo("150.00");
        assertThat(r.closes()).isFalse();
        assertThat(r.warnings()).anySatisfy(w -> {
            assertThat(w.code()).isEqualTo("BRIDGE_DOES_NOT_CLOSE");
            assertThat(w.text()).isEqualTo("a ponte não fecha: R$ 150,00 não explicado");
        });
    }

    @Test
    void closingOfThePreviousMonthIsTheOpeningBalance() {
        // August loaded with the operating fund closing 310.735,05 (the September opening)
        UUID august = UUID.randomUUID();
        YearMonth aug = YearMonth.of(2026, 8);
        s.cashFlows.add(SyntheticSeptember.cashFlow(august, aug, aug.atEndOfMonth()));
        s.balances.add(SyntheticSeptember.balance(august, aug, OPERATING, "CONDOMÍNIO", "300000.00", "10735.05",
                "0.00", "310735.05"));

        BalanceBridgeResponse r = bridge(OPERATING, SEPTEMBER, SEPTEMBER);

        assertThat(r.openingSource()).isEqualTo("saldo final do extrato de 08/2026");
        assertThat(r.openingBalance()).isEqualByComparingTo("310735.05");
        assertThat(r.closes()).isTrue();
    }

    @Test
    void partialMonthHasItsColumnButIsOutOfTheTotal() {
        UUID october = UUID.randomUUID();
        s.cashFlows.add(SyntheticSeptember.cashFlow(october, OCTOBER, OCTOBER.atEndOfMonth()));
        s.partialUntil.put(october, LocalDate.of(2026, 10, 20));
        s.entries.add(s.entry(october, OPERATING, "3001", "1000.00", "0.00", false, true, OCTOBER.atDay(5)));
        s.balances.add(SyntheticSeptember.balance(october, OCTOBER, OPERATING, "CONDOMÍNIO", "297514.48", "1000.00",
                "0.00", "298514.48"));

        BalanceBridgeResponse r = bridge(OPERATING, SEPTEMBER, OCTOBER);

        assertThat(r.closingBalance()).isEqualByComparingTo("297514.48");
        assertThat(r.variation()).isEqualByComparingTo("-13220.57");
        assertThat(r.months()).filteredOn(m -> m.month().equals("2026-10")).singleElement().satisfies(m -> {
            assertThat(m.inTotal()).isFalse();
            assertThat(m.partialUntil()).isEqualTo(LocalDate.of(2026, 10, 20));
            assertThat(m.variation()).isEqualByComparingTo("1000.00");
        });
        assertThat(r.warnings()).anySatisfy(w -> assertThat(w.code()).isEqualTo("PARTIAL_MONTH"));
    }

    private BalanceBridgeResponse bridge(UUID fund, YearMonth from, YearMonth to) {
        var grid = s.grid(s.confirmedProfiles()).result();
        var funds = FundMovements.calculate(s.funds(from.minusMonths(1), to));
        return BalanceBridge.calculate(new BalanceBridge.Input(grid, funds, fund, OPERATING, from, to));
    }

    static BridgeItemResponse item(BalanceBridgeResponse r, BalanceBridgeItemCode code) {
        return r.items().stream().filter(i -> i.code() == code).findFirst().orElseThrow();
    }
}
