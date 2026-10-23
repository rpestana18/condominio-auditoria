package br.com.condominioauditoria.api.service.calculator;

import static br.com.condominioauditoria.api.service.calculator.SyntheticSeptember.OCTOBER;
import static br.com.condominioauditoria.api.service.calculator.SyntheticSeptember.OPERATING;
import static br.com.condominioauditoria.api.service.calculator.SyntheticSeptember.RESERVE;
import static br.com.condominioauditoria.api.service.calculator.SyntheticSeptember.SEPTEMBER;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.api.dto.response.budget.BalanceBridgeResponse;
import br.com.condominioauditoria.api.dto.response.budget.BlockAccountResponse;
import br.com.condominioauditoria.api.dto.response.budget.BridgeItemResponse;
import br.com.condominioauditoria.api.dto.response.budget.BridgeLineResponse;
import br.com.condominioauditoria.api.dto.response.budget.BridgeMonthResponse;
import br.com.condominioauditoria.api.model.enums.BalanceBridgeItemCode;
import br.com.condominioauditoria.api.model.enums.BudgetVsActualStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
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
        // Q49: the income is only the operating fund's fee; the reserve fund is an apportionment fund
        BridgeItemResponse income = item(r, BalanceBridgeItemCode.INCOME_VS_PLANNED);
        assertThat(income.label()).isEqualTo("Cota ordinária acima ou abaixo do previsto");
        assertThat(income.amount()).isEqualByComparingTo("-10385.57");
        BridgeItemResponse funds = item(r, BalanceBridgeItemCode.APPORTIONMENT_FUNDS);
        assertThat(funds.amount()).isEqualByComparingTo("14060.79");
        assertThat(funds.percentageOfVariation()).isNull();
        assertThat(item(r, BalanceBridgeItemCode.INTER_FUND_TRANSFERS).amount()).isEqualByComparingTo("0.00");
        assertThat(item(r, BalanceBridgeItemCode.UNEXPLAINED).amount()).isEqualByComparingTo("0.00");
        assertThat(r.items()).extracting(BridgeItemResponse::code).doesNotContain(
                BalanceBridgeItemCode.FUND_PLANNED_COLLECTION, BalanceBridgeItemCode.FUND_EXPENSES);
        assertThat(r.closes()).isTrue();
    }

    @Test
    void allFundsBridgeOfTheFiscalYearHasTheApportionmentFundsApart() {
        // Q49, with the numbers of RF-13.6 (May to September/2026); transfers between funds net 0,00 here
        fiscalYearMayToSeptember();

        BalanceBridgeResponse r = bridge(null, YearMonth.of(2026, 5), SEPTEMBER);

        assertThat(r.effectiveFrom()).isEqualTo("2026-05");
        assertThat(r.effectiveTo()).isEqualTo("2026-09");
        assertThat(r.items()).extracting(BridgeItemResponse::code).containsExactly(
                BalanceBridgeItemCode.LINE_SAVINGS, BalanceBridgeItemCode.PROVISIONS_RESERVED,
                BalanceBridgeItemCode.SEPARATE_APPORTIONMENT, BalanceBridgeItemCode.INCOME_VS_PLANNED,
                BalanceBridgeItemCode.OUTSIDE_BUDGET, BalanceBridgeItemCode.INTER_FUND_TRANSFERS,
                BalanceBridgeItemCode.APPORTIONMENT_FUNDS, BalanceBridgeItemCode.UNEXPLAINED);
        BridgeItemResponse income = item(r, BalanceBridgeItemCode.INCOME_VS_PLANNED);
        assertThat(income.label()).isEqualTo("Cota ordinária acima ou abaixo do previsto");
        assertThat(income.amount()).isEqualByComparingTo("171373.73");
        assertThat(income.parts()).extracting(p -> p.code() + "=" + p.amount().toPlainString())
                .containsExactly("CONDO_FEE=2415892.07", "OTHER_INCOME=13582.31", "PLANNED=-2258100.65");
        BridgeItemResponse funds = item(r, BalanceBridgeItemCode.APPORTIONMENT_FUNDS);
        assertThat(funds.label()).isEqualTo("Fundos de rateio: entradas − saídas");
        assertThat(funds.amount()).isEqualByComparingTo("140404.60");
        assertThat(funds.percentageOfVariation()).isNull();
        assertThat(funds.parts()).extracting(p -> p.code() + "=" + p.amount().toPlainString())
                .containsExactly("INFLOWS=1575122.53", "OUTFLOWS=-1434717.93");
        assertThat(funds.funds()).extracting(f -> f.name() + "=" + f.amount().toPlainString()).containsExactly(
                "MERCADO=98492.85", "ENERGIA ELETRICA=73119.97", "FUNDO DE RESERVA=11022.53",
                "OBRAS / REFORMAS / INFRA=-42230.75");
        assertThat(funds.funds()).filteredOn(f -> f.name().equals("ENERGIA ELETRICA")).singleElement()
                .satisfies(f -> {
                    assertThat(f.inflows()).isEqualByComparingTo("800000.00");
                    assertThat(f.outflows()).isEqualByComparingTo("726880.03");
                });
        assertThat(funds.funds().stream().map(f -> f.amount()).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("140404.60");
        assertThat(item(r, BalanceBridgeItemCode.INTER_FUND_TRANSFERS).amount()).isEqualByComparingTo("0.00");
        assertThat(item(r, BalanceBridgeItemCode.UNEXPLAINED).amount()).isEqualByComparingTo("0.00");
        assertThat(r.closes()).isTrue();
        assertThat(r.items().stream().map(BridgeItemResponse::amount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(r.variation());
        // No share passes 100% of the variation, and the old items of the other funds are gone
        assertThat(r.items()).allSatisfy(i -> {
            if (i.percentageOfVariation() != null) {
                assertThat(i.percentageOfVariation().abs()).isLessThanOrEqualTo(new BigDecimal("100"));
            }
        });
        assertThat(r.items()).extracting(BridgeItemResponse::code).doesNotContain(
                BalanceBridgeItemCode.FUND_PLANNED_COLLECTION, BalanceBridgeItemCode.FUND_EXPENSES);
        assertThat(r.months()).allSatisfy(m -> assertThat(m.items()).extracting(i -> i.code())
                .doesNotContain(BalanceBridgeItemCode.FUND_PLANNED_COLLECTION, BalanceBridgeItemCode.FUND_EXPENSES));
    }

    @Test
    void operatingFundAndAllFundsShareTheOperatingItems() {
        fiscalYearMayToSeptember();

        BalanceBridgeResponse all = bridge(null, YearMonth.of(2026, 5), SEPTEMBER);
        BalanceBridgeResponse operating = bridge(OPERATING, YearMonth.of(2026, 5), SEPTEMBER);

        for (BalanceBridgeItemCode code : new BalanceBridgeItemCode[] {BalanceBridgeItemCode.LINE_SAVINGS,
                BalanceBridgeItemCode.PROVISIONS_RESERVED, BalanceBridgeItemCode.INCOME_VS_PLANNED,
                BalanceBridgeItemCode.OUTSIDE_BUDGET}) {
            assertThat(item(all, code).amount()).as(code.name()).isEqualByComparingTo(item(operating, code).amount());
        }
        // The operating fund's bridge (and the home summary, RF-13.7) has no apportionment funds
        assertThat(operating.items()).extracting(BridgeItemResponse::code)
                .doesNotContain(BalanceBridgeItemCode.APPORTIONMENT_FUNDS);
        assertThat(item(operating, BalanceBridgeItemCode.INCOME_VS_PLANNED).label())
                .isEqualTo("Receita acima ou abaixo do previsto");
        assertThat(item(operating, BalanceBridgeItemCode.UNEXPLAINED).amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void oneFundOtherThanTheOperatingKeepsItsPlannedCollection() {
        BalanceBridgeResponse r = bridge(RESERVE, SEPTEMBER, SEPTEMBER);

        assertThat(item(r, BalanceBridgeItemCode.FUND_PLANNED_COLLECTION).amount()).isEqualByComparingTo("13548.60");
        assertThat(item(r, BalanceBridgeItemCode.FUND_EXPENSES).amount()).isEqualByComparingTo("-200.00");
        assertThat(r.items()).extracting(BridgeItemResponse::code)
                .doesNotContain(BalanceBridgeItemCode.APPORTIONMENT_FUNDS);
    }

    @Test
    void monthInProgressShowsOnlyWhatCameInAndWentOut() {
        // Q50, with the numbers of RF-13.6 and RF-13.10: October/2026 up to 20/10
        october();

        BalanceBridgeResponse r = bridge(OPERATING, SEPTEMBER, OCTOBER);

        BridgeMonthResponse oct = r.months().stream().filter(m -> m.month().equals("2026-10")).findFirst()
                .orElseThrow();
        assertThat(oct.inTotal()).isFalse();
        assertThat(oct.openingBalance()).isEqualByComparingTo("297514.48");
        assertThat(oct.inflows()).isEqualByComparingTo("459045.72");
        assertThat(oct.outflows()).isEqualByComparingTo("374579.86");
        assertThat(oct.variation()).isEqualByComparingTo("84465.86");
        assertThat(oct.closingBalance()).isEqualByComparingTo("381980.34");
        assertThat(oct.items()).isNotEmpty().allSatisfy(i -> assertThat(i.amount()).isNull());
        assertThat(oct.items()).extracting(i -> i.code()).contains(BalanceBridgeItemCode.LINE_SAVINGS,
                BalanceBridgeItemCode.PROVISIONS_RESERVED, BalanceBridgeItemCode.INCOME_VS_PLANNED,
                BalanceBridgeItemCode.OUTSIDE_BUDGET, BalanceBridgeItemCode.INTER_FUND_TRANSFERS,
                BalanceBridgeItemCode.UNEXPLAINED);
        assertThat(r.partialMonth().inflows()).isEqualByComparingTo("459045.72");
        assertThat(r.partialMonth().outflows()).isEqualByComparingTo("374579.86");
        assertThat(r.partialMonth().variation()).isEqualByComparingTo("84465.86");
        assertThat(r.partialMonth().closingBalance()).isEqualByComparingTo("381980.34");
        // The total stays with the complete months only
        assertThat(r.variation()).isEqualByComparingTo("-13220.57");
        assertThat(r.months().stream().filter(m -> m.month().equals("2026-09")).findFirst().orElseThrow().items())
                .allSatisfy(i -> assertThat(i.amount()).isNotNull());
        assertThat(r.months().getFirst().inflows()).isEqualByComparingTo("441234.56");
        assertThat(r.months().getFirst().outflows()).isEqualByComparingTo("454455.13");
    }

    @Test
    void monthInProgressOfAllFundsHasTheApportionmentFundsBlankToo() {
        october();

        BalanceBridgeResponse r = bridge(null, SEPTEMBER, OCTOBER);

        BridgeMonthResponse oct = r.months().stream().filter(m -> m.month().equals("2026-10")).findFirst()
                .orElseThrow();
        assertThat(oct.inflows()).isEqualByComparingTo("706962.28");
        assertThat(oct.outflows()).isEqualByComparingTo("555365.13");
        assertThat(oct.variation()).isEqualByComparingTo("151597.15");
        assertThat(oct.items()).extracting(i -> i.code()).contains(BalanceBridgeItemCode.APPORTIONMENT_FUNDS);
        assertThat(oct.items()).allSatisfy(i -> assertThat(i.amount()).isNull());
        assertThat(r.variation()).isEqualByComparingTo("5840.22");
        assertThat(item(r, BalanceBridgeItemCode.APPORTIONMENT_FUNDS).amount()).isEqualByComparingTo("14060.79");
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
        assertThat(r.warnings()).anySatisfy(w -> {
            assertThat(w.code()).isEqualTo("PARTIAL_MONTH");
            assertThat(w.text()).isEqualTo("10/2026 parcial: aparece na sua coluna, fora do total (lançamentos até"
                    + " 20/10/2026; variação de R$ 1.000,00 à parte); o total vai até 30/09/2026");
        });
        // D4: the date the total closes, the effective period and the partial month apart
        assertThat(r.from()).isEqualTo("2026-09");
        assertThat(r.to()).isEqualTo("2026-10");
        assertThat(r.effectiveFrom()).isEqualTo("2026-09");
        assertThat(r.effectiveTo()).isEqualTo("2026-09");
        assertThat(r.openingDate()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(r.closingDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(r.partialMonth()).isNotNull();
        assertThat(r.partialMonth().month()).isEqualTo("2026-10");
        assertThat(r.partialMonth().partialUntil()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(r.partialMonth().openingBalance()).isEqualByComparingTo("297514.48");
        assertThat(r.partialMonth().closingBalance()).isEqualByComparingTo("298514.48");
        assertThat(r.partialMonth().variation()).isEqualByComparingTo("1000.00");
    }

    @Test
    void fiscalYearPeriodSaysWhereTheTotalReallyStartsAndEnds() {
        // Default period of the screen: the whole fiscal year, with only September loaded
        BalanceBridgeResponse r = bridge(OPERATING, YearMonth.of(2026, 5), YearMonth.of(2027, 4));

        assertThat(r.from()).isEqualTo("2026-05");
        assertThat(r.to()).isEqualTo("2027-04");
        assertThat(r.effectiveFrom()).isEqualTo("2026-09");
        assertThat(r.effectiveTo()).isEqualTo("2026-09");
        assertThat(r.openingDate()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(r.closingDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(r.closingBalance()).isEqualByComparingTo("297514.48");
        assertThat(r.partialMonth()).isNull();
        assertThat(r.closes()).isTrue();
    }

    @Test
    void openingFromThePreviousMonthStartsTheEffectivePeriodAtFrom() {
        UUID august = UUID.randomUUID();
        YearMonth aug = YearMonth.of(2026, 8);
        s.cashFlows.add(SyntheticSeptember.cashFlow(august, aug, aug.atEndOfMonth()));
        s.balances.add(SyntheticSeptember.balance(august, aug, OPERATING, "CONDOMÍNIO", "300000.00", "10735.05",
                "0.00", "310735.05"));

        BalanceBridgeResponse r = bridge(OPERATING, SEPTEMBER, SEPTEMBER);

        assertThat(r.effectiveFrom()).isEqualTo("2026-09");
        assertThat(r.openingDate()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(r.closingDate()).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    void accountsWithoutConfirmedMappingAreWarnedWithTheirAmount() {
        // D6: debits of the operating fund in two accounts without any mapping
        s.entries.add(s.entry(s.septemberFile, OPERATING, "9001", "0.00", "700.00", false, false,
                SEPTEMBER.atDay(10)));
        s.entries.add(s.entry(s.septemberFile, OPERATING, "9002", "0.00", "50.00", false, false,
                SEPTEMBER.atDay(11)));
        s.entries.add(s.entry(s.septemberFile, OPERATING, "9001", "0.00", "300.00", false, false,
                SEPTEMBER.atDay(12)));

        BalanceBridgeResponse r = bridge(OPERATING, SEPTEMBER, SEPTEMBER);

        assertThat(item(r, BalanceBridgeItemCode.OUTSIDE_BUDGET).parts())
                .filteredOn(p -> p.code().equals("WITHOUT_BUDGET_LINE")).singleElement()
                .satisfies(p -> assertThat(p.amount()).isEqualByComparingTo("-1050.00"));
        assertThat(r.warnings()).filteredOn(w -> w.code().equals("NO_CONFIRMED_MAPPING")).singleElement()
                .satisfies(w -> assertThat(w.text()).isEqualTo("2 contas sem de-para confirmado no total da ponte:"
                        + " R$ 1.050,00 em \"sem linha da PO\", fora das linhas da PO; ficam em \"Fora da PO\" e"
                        + " \"Economia nas linhas\" pode incluir o previsto dessas despesas. Contas: 9001 CONTA 9001"
                        + " R$ 1.000,00; 9002 CONTA 9002 R$ 50,00"));
        // Another fund alone: the operating fund's accounts are not part of its bridge
        assertThat(bridge(RESERVE, SEPTEMBER, SEPTEMBER).warnings())
                .noneMatch(w -> w.code().equals("NO_CONFIRMED_MAPPING"));
        assertThat(bridge(null, SEPTEMBER, SEPTEMBER).warnings())
                .anyMatch(w -> w.code().equals("NO_CONFIRMED_MAPPING"));
    }

    @Test
    void mappingStateComesAsFieldsWithTheProvisionalFlag() {
        s.entries.add(s.entry(s.septemberFile, OPERATING, "9001", "0.00", "700.00", false, false,
                SEPTEMBER.atDay(10)));
        s.entries.add(s.entry(s.septemberFile, OPERATING, "9002", "0.00", "50.00", false, false,
                SEPTEMBER.atDay(11)));

        BalanceBridgeResponse r = bridge(OPERATING, SEPTEMBER, SEPTEMBER);

        assertThat(r.mapping()).isNotNull();
        assertThat(r.mapping().unmappedAccounts()).isEqualTo(2);
        assertThat(r.mapping().unmappedAmount()).isEqualByComparingTo("750.00");
        assertThat(r.mapping().accounts()).extracting(a -> a.account()).containsExactly("9001", "9002");
        assertThat(r.mapping().provisionalLimitPercentage()).isEqualByComparingTo("5.00");
        // R$ 750,00 is far below 5% of the operating fund's debits of the month (without transfers)
        assertThat(r.mapping().debits()).isGreaterThan(new BigDecimal("15000.00"));
        assertThat(r.mapping().provisional()).isFalse();
        assertThat(bridge(RESERVE, SEPTEMBER, SEPTEMBER).mapping()).isNull();
    }

    @Test
    void mappingIsProvisionalAboveTheLimitOnly() {
        var accounts = List.of(new BlockAccountResponse("9001", "CONTA", "sem de-para", new BigDecimal("50.01"), 1));
        var unmapped = new BudgetTrackingGrid.UnmappedAccounts(accounts, new BigDecimal("50.01"), 1);
        assertThat(BalanceBridge.mapping(unmapped, new BigDecimal("1000.00")).provisional()).isTrue();
        var atLimit = new BudgetTrackingGrid.UnmappedAccounts(accounts, new BigDecimal("50.00"), 1);
        assertThat(BalanceBridge.mapping(atLimit, new BigDecimal("1000.00")).provisional()).isFalse();
        var none = new BudgetTrackingGrid.UnmappedAccounts(List.of(), new BigDecimal("0.00"), 0);
        assertThat(BalanceBridge.mapping(none, new BigDecimal("0.00")).provisional()).isFalse();
    }

    @Test
    void accountsWithoutMappingOnlyInThePartialMonthAreNotInTheTotalWarning() {
        UUID october = UUID.randomUUID();
        s.cashFlows.add(SyntheticSeptember.cashFlow(october, OCTOBER, OCTOBER.atEndOfMonth()));
        s.partialUntil.put(october, LocalDate.of(2026, 10, 20));
        s.entries.add(s.entry(october, OPERATING, "9001", "0.00", "700.00", false, false, OCTOBER.atDay(5)));
        s.balances.add(SyntheticSeptember.balance(october, OCTOBER, OPERATING, "CONDOMÍNIO", "297514.48", "0.00",
                "700.00", "296814.48"));

        BalanceBridgeResponse r = bridge(OPERATING, SEPTEMBER, OCTOBER);

        assertThat(r.warnings()).noneMatch(w -> w.code().equals("NO_CONFIRMED_MAPPING"));
    }

    /**
     * October/2026 in progress up to 20/10 (RF-13.10): CONDOMÍNIO 459.045,72 in and 374.579,86 out; the reserve fund
     * 247.916,56 in and 180.785,27 out (all funds: 706.962,28 and 555.365,13).
     */
    private void october() {
        UUID october = UUID.randomUUID();
        s.cashFlows.add(SyntheticSeptember.cashFlow(october, OCTOBER, OCTOBER.atEndOfMonth()));
        s.partialUntil.put(october, LocalDate.of(2026, 10, 20));
        s.entries.add(s.entry(october, OPERATING, "3001", "459045.72", "0.00", false, true, OCTOBER.atDay(5)));
        s.entries.add(s.entry(october, OPERATING, s.account("1.1.1"), "0.00", "374579.86", false, false,
                OCTOBER.atDay(6)));
        s.entries.add(s.entry(october, RESERVE, "3001", "247916.56", "0.00", false, true, OCTOBER.atDay(5)));
        s.entries.add(s.entry(october, RESERVE, "4001", "0.00", "180785.27", false, false, OCTOBER.atDay(6)));
        s.balances.add(SyntheticSeptember.balance(october, OCTOBER, OPERATING, "CONDOMÍNIO", "297514.48",
                "459045.72", "374579.86", "381980.34"));
        s.balances.add(SyntheticSeptember.balance(october, OCTOBER, RESERVE, "FUNDO DE RESERVA", "20060.79",
                "247916.56", "180785.27", "87192.08"));
    }

    /**
     * May to September/2026 with the totals of RF-13.6: operating fee 2.415.892,07 and other income 13.582,31 over
     * five months of 451.620,13 planned; four apportionment funds with 1.575.122,53 in and 1.434.717,93 out (reserve
     * +11.022,53, works −42.230,75, power 27.249,95 to 100.369,92 = +73.119,97 and one more fund +98.492,85). The extra
     * movement is in May; June to August only carry the balances.
     */
    private void fiscalYearMayToSeptember() {
        UUID works = UUID.randomUUID();
        UUID power = UUID.randomUUID();
        UUID market = UUID.randomUUID();
        s.fundNames.put(works, "OBRAS / REFORMAS / INFRA");
        s.fundNames.put(power, "ENERGIA ELETRICA");
        s.fundNames.put(market, "MERCADO");
        YearMonth may = YearMonth.of(2026, 5);
        UUID mayFile = UUID.randomUUID();
        s.cashFlows.add(SyntheticSeptember.cashFlow(mayFile, may, may.atEndOfMonth()));
        LocalDate day = may.atDay(10);
        s.entries.add(s.entry(mayFile, OPERATING, "3001", "1975892.07", "0.00", false, true, day));
        s.entries.add(s.entry(mayFile, OPERATING, "3002", "12347.75", "0.00", false, false, day));
        s.entries.add(s.entry(mayFile, RESERVE, "4001", "0.00", "3038.26", false, false, day));
        s.entries.add(s.entry(mayFile, works, "3001", "50000.00", "0.00", false, true, day));
        s.entries.add(s.entry(mayFile, works, "4002", "0.00", "92230.75", false, false, day));
        s.entries.add(s.entry(mayFile, power, "3001", "800000.00", "0.00", false, true, day));
        s.entries.add(s.entry(mayFile, power, "4003", "0.00", "726880.03", false, false, day));
        s.entries.add(s.entry(mayFile, market, "3002", "710861.74", "0.00", false, false, day));
        s.entries.add(s.entry(mayFile, market, "4004", "0.00", "612368.89", false, false, day));
        s.balances.add(SyntheticSeptember.balance(mayFile, may, OPERATING, "CONDOMÍNIO", "100000.00",
                "1988239.82", "0.00", "2088239.82"));
        s.balances.add(SyntheticSeptember.balance(mayFile, may, RESERVE, "FUNDO DE RESERVA", "4038.26", "0.00",
                "3038.26", "1000.00"));
        s.balances.add(SyntheticSeptember.balance(mayFile, may, works, "OBRAS / REFORMAS / INFRA", "100000.00",
                "50000.00", "92230.75", "57769.25"));
        s.balances.add(SyntheticSeptember.balance(mayFile, may, power, "ENERGIA ELETRICA", "27249.95", "800000.00",
                "726880.03", "100369.92"));
        s.balances.add(SyntheticSeptember.balance(mayFile, may, market, "MERCADO", "0.00", "710861.74",
                "612368.89", "98492.85"));
        for (YearMonth m = may.plusMonths(1); m.isBefore(SEPTEMBER); m = m.plusMonths(1)) {
            UUID file = UUID.randomUUID();
            s.cashFlows.add(SyntheticSeptember.cashFlow(file, m, m.atEndOfMonth()));
            s.balances.add(SyntheticSeptember.balance(file, m, OPERATING, "CONDOMÍNIO", "2088239.82", "0.00", "0.00",
                    "2088239.82"));
            s.balances.add(SyntheticSeptember.balance(file, m, RESERVE, "FUNDO DE RESERVA", "1000.00", "0.00",
                    "0.00", "1000.00"));
            carry(file, m, works, "OBRAS / REFORMAS / INFRA", "57769.25");
            carry(file, m, power, "ENERGIA ELETRICA", "100369.92");
            carry(file, m, market, "MERCADO", "98492.85");
        }
        // September: the operating fund opens with what May left; the other funds only carry their balances
        s.balances.removeIf(b -> b.getFundId().equals(OPERATING) && b.getFileId().equals(s.septemberFile));
        s.balances.add(SyntheticSeptember.balance(s.septemberFile, SEPTEMBER, OPERATING, "CONDOMÍNIO",
                "2088239.82", "441234.56", "454455.13", "2075019.25"));
        carry(s.septemberFile, SEPTEMBER, works, "OBRAS / REFORMAS / INFRA", "57769.25");
        carry(s.septemberFile, SEPTEMBER, power, "ENERGIA ELETRICA", "100369.92");
        carry(s.septemberFile, SEPTEMBER, market, "MERCADO", "98492.85");
    }

    private void carry(UUID file, YearMonth m, UUID fund, String name, String balance) {
        s.balances.add(SyntheticSeptember.balance(file, m, fund, name, balance, "0.00", "0.00", balance));
    }

    private BalanceBridgeResponse bridge(UUID fund, YearMonth from, YearMonth to) {
        var grid = s.grid(s.confirmedProfiles());
        var funds = FundMovements.calculate(s.funds(from.minusMonths(1), to));
        return BalanceBridge.calculate(BalanceBridge.Input.of(grid, funds, fund, OPERATING, from, to));
    }

    static BridgeItemResponse item(BalanceBridgeResponse r, BalanceBridgeItemCode code) {
        return r.items().stream().filter(i -> i.code() == code).findFirst().orElseThrow();
    }
}
