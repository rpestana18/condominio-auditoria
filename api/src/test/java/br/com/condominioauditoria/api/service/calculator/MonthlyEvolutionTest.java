package br.com.condominioauditoria.api.service.calculator;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.api.dto.response.dashboard.MonthlyEvolutionResponse;
import br.com.condominioauditoria.api.dto.response.dashboard.MonthlyEntryResponse;
import br.com.condominioauditoria.api.dto.response.dashboard.MonthlyPointResponse;
import br.com.condominioauditoria.api.dto.response.dashboard.MonthlySeriesResponse;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.Enrichment;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.LedgerEntryData;
import br.com.condominioauditoria.api.model.accounting.LedgerEntry;
import br.com.condominioauditoria.api.model.enums.MonthlyPointStatus;
import br.com.condominioauditoria.api.model.enums.UtilityType;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.CashFlowFile;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * RF-12.2 to RF-12.5 with the utility bills of the pilot's September/2026 cash flow: water and sewage 1896 (48,962.57 +
 * 95,129.74), electricity 1894 (8,913.65 + 14,807.59) and gas 3611 (807.17 + 1,746.00, paid by the "Locação espaço
 * comum" fund).
 */
class MonthlyEvolutionTest {

    private static final UUID CONDOMINIUM = UUID.randomUUID();
    private static final UUID OPERATING = UUID.randomUUID();
    private static final UUID WATER_FUND = UUID.randomUUID();
    private static final UUID ELECTRICITY_FUND = UUID.randomUUID();
    private static final UUID LEASE_FUND = UUID.randomUUID();
    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
    private static final YearMonth AUGUST = YearMonth.of(2026, 8);
    private static final Map<String, UtilityType> CONFIRMED = Map.of("1896", UtilityType.WATER, "1894",
            UtilityType.ELECTRICITY, "3611", UtilityType.GAS);

    private final List<LedgerEntry> entries = new ArrayList<>();
    private final List<CashFlowFile> cashFlows = new ArrayList<>();

    @Test
    void septemberUtilitiesSumEveryBillOfTheMonthInAnyFund() {
        UUID september = september();

        MonthlyEvolutionResponse r = calculate(SEPTEMBER);

        assertThat(r.months()).hasSize(12).startsWith("2025-10").endsWith("2026-09");
        assertThat(r.utilities()).extracting(MonthlySeriesResponse::key).containsExactly("WATER", "ELECTRICITY", "GAS");
        assertThat(last(series(r, "WATER")).amount()).isEqualByComparingTo("144092.31");
        assertThat(last(series(r, "ELECTRICITY")).amount()).isEqualByComparingTo("23721.24");
        assertThat(last(series(r, "GAS")).amount()).isEqualByComparingTo("2553.17");
        MonthlyPointResponse water = last(series(r, "WATER"));
        assertThat(water.entries()).extracting(MonthlyEntryResponse::amount).map(BigDecimal::toPlainString)
                .containsExactly("48962.57", "95129.74");
        assertThat(water.entries()).allSatisfy(e -> {
            assertThat(e.fileId()).isEqualTo(september);
            assertThat(e.referenceMonth()).isEqualTo("2026-08");
            assertThat(e.fund()).isEqualTo("AGUA/ESGOTO");
        });
        assertThat(last(series(r, "GAS")).entries()).extracting(MonthlyEntryResponse::fund)
                .containsOnly("LOCAÇÃO ESPAÇO COMUM");
    }

    @Test
    void monthsWithoutCashFlowAreNeverZeroAndHaveNoBaseOfComparison() {
        september();

        MonthlySeriesResponse electricity = series(calculate(SEPTEMBER), "ELECTRICITY");

        assertThat(electricity.points().subList(0, 11)).allSatisfy(p -> {
            assertThat(p.status()).isEqualTo(MonthlyPointStatus.NO_CASH_FLOW);
            assertThat(p.amount()).isNull();
        });
        assertThat(last(electricity).previousMonth()).isNull();
        assertThat(last(electricity).previousYear()).isNull();
    }

    @Test
    void variationAgainstPreviousMonthAndSameMonthOfPreviousYear() {
        september();
        UUID august = file(AUGUST);
        debit(august, ELECTRICITY_FUND, "1894", "20000.00", LocalDate.of(2026, 8, 28), "REF. 07/2026");
        UUID lastYear = file(YearMonth.of(2025, 9));
        debit(lastYear, ELECTRICITY_FUND, "1894", "25000.00", LocalDate.of(2025, 9, 26), "REF. 08/2025");

        MonthlyPointResponse september = last(series(calculate(SEPTEMBER), "ELECTRICITY"));

        assertThat(september.previousMonth().amount()).isEqualByComparingTo("3721.24");
        assertThat(september.previousMonth().percentage()).isEqualByComparingTo("18.6");
        assertThat(september.previousYear().amount()).isEqualByComparingTo("-1278.76");
        assertThat(september.previousYear().percentage()).isEqualByComparingTo("-5.1");
    }

    @Test
    void monthWithCashFlowAndNoBillIsNotZero() {
        september();
        file(AUGUST);

        MonthlyPointResponse august = series(calculate(SEPTEMBER), "GAS").points().get(10);

        assertThat(august.status()).isEqualTo(MonthlyPointStatus.NO_ENTRY);
        assertThat(august.amount()).isNull();
        assertThat(last(series(calculate(SEPTEMBER), "GAS")).previousMonth()).isNull();
    }

    @Test
    void twoCashFlowsInTheSameMonthGiveNoAmount() {
        september();
        file(SEPTEMBER);

        MonthlyPointResponse september = last(series(calculate(SEPTEMBER), "WATER"));

        assertThat(september.status()).isEqualTo(MonthlyPointStatus.TWO_CASH_FLOWS);
        assertThat(september.amount()).isNull();
    }

    @Test
    void condoFeeIsTheReceiptsOfTheOperatingFundOnly() {
        UUID september = september();
        entries.add(entry(september, OPERATING, null, "10814.09", "0.00", false, true, LocalDate.of(2026, 9, 1),
                "RECIBOS"));
        entries.add(entry(september, OPERATING, null, "54192.37", "0.00", false, true, LocalDate.of(2026, 9, 10),
                "RECIBOS"));
        entries.add(entry(september, OPERATING, "1317", "5000.00", "0.00", true, false, LocalDate.of(2026, 9, 11),
                "TRANSFERÊNCIA ENTRE CONTAS"));
        entries.add(entry(september, WATER_FUND, null, "130117.12", "0.00", false, true, LocalDate.of(2026, 9, 2),
                "RECIBOS"));

        MonthlyEvolutionResponse r = calculate(SEPTEMBER);

        assertThat(r.operatingFund()).isEqualTo("CONDOMÍNIO");
        assertThat(last(r.condoFee()).amount()).isEqualByComparingTo("65006.46");
        assertThat(last(r.condoFee()).entries()).hasSize(2);
    }

    @Test
    void withoutOperatingFundOrConfirmedAccountsThereAreNoSeries() {
        september();

        MonthlyEvolutionResponse r = MonthlyEvolution.calculate(new MonthlyEvolution.Input(SEPTEMBER, cashFlows,
                entries, null, null, Map.of(), Map.of(), 3));

        assertThat(r.condoFee()).isNull();
        assertThat(r.utilities()).isEmpty();
        assertThat(r.pendingUtilityAccounts()).isEqualTo(3);
    }

    @Test
    void referenceMonthComesFromTheMemo() {
        assertThat(MonthlyEvolution.referenceMonth("CONSUMO DE ENERGIA DO CONDOMINIO REF. 08/2026, RECIBO: DE: LIGHT"))
                .isEqualTo("2026-08");
        assertThat(MonthlyEvolution.referenceMonth("GAS DAS AREAS COMUNS - DEBITO AUTOMATICO - REF.  8/2026"))
                .isEqualTo("2026-08");
        assertThat(MonthlyEvolution.referenceMonth("SEM REFERÊNCIA")).isNull();
        assertThat(MonthlyEvolution.referenceMonth("REF. 13/2026")).isNull();
    }

    private UUID september() {
        UUID file = file(SEPTEMBER);
        debit(file, WATER_FUND, "1896", "48962.57", LocalDate.of(2026, 9, 21), "AGUA/ESGOTO BL 03 - REF. 08/2026");
        debit(file, WATER_FUND, "1896", "95129.74", LocalDate.of(2026, 9, 21), "AGUA/ESGOTO BL 01 - REF. 08/2026");
        debit(file, ELECTRICITY_FUND, "1894", "8913.65", LocalDate.of(2026, 9, 28), "CONSUMO REF. 08/2026");
        debit(file, ELECTRICITY_FUND, "1894", "14807.59", LocalDate.of(2026, 9, 28), "CONSUMO REF. 08/2026");
        debit(file, LEASE_FUND, "3611", "807.17", LocalDate.of(2026, 9, 8), "GAS - REF. 08/2026");
        debit(file, LEASE_FUND, "3611", "1746.00", LocalDate.of(2026, 9, 8), "GAS - REF. 08/2026");
        // Adjustment between utility funds: not a confirmed account
        debit(file, WATER_FUND, "3775", "153.48", LocalDate.of(2026, 9, 21), "AJUSTE CONTÁBIL");
        return file;
    }

    private UUID file(YearMonth month) {
        UUID id = UUID.randomUUID();
        cashFlows.add(new CashFlowFile(id, "fluxo-" + month + ".pdf", "f".repeat(64), month.atDay(1),
                month.atEndOfMonth(), Instant.EPOCH, "gestor"));
        return id;
    }

    private void debit(UUID file, UUID fund, String account, String amount, LocalDate date, String memo) {
        entries.add(entry(file, fund, account, "0.00", amount, false, false, date, memo));
    }

    private LedgerEntry entry(UUID file, UUID fund, String account, String credit, String debit, boolean transfer,
            boolean fee, LocalDate date, String memo) {
        var read = new LedgerEntryData(3, entries.size() + 1, date, account, account == null ? "" : "CONTA " + account,
                "", memo, new BigDecimal(credit), new BigDecimal(debit), BigDecimal.ZERO.setScale(2),
                new Enrichment(null, null, null, transfer, fee));
        return new LedgerEntry(CONDOMINIUM, file, fund, read);
    }

    private MonthlyEvolutionResponse calculate(YearMonth until) {
        return MonthlyEvolution.calculate(new MonthlyEvolution.Input(until, cashFlows, entries, OPERATING,
                "CONDOMÍNIO", Map.of(OPERATING, "CONDOMÍNIO", WATER_FUND, "AGUA/ESGOTO", ELECTRICITY_FUND,
                        "ENERGIA ELETRICA", LEASE_FUND, "LOCAÇÃO ESPAÇO COMUM"), CONFIRMED, 0));
    }

    private static MonthlySeriesResponse series(MonthlyEvolutionResponse r, String key) {
        return r.utilities().stream().filter(s -> s.key().equals(key)).findFirst().orElseThrow();
    }

    private static MonthlyPointResponse last(MonthlySeriesResponse s) {
        return s.points().getLast();
    }
}
