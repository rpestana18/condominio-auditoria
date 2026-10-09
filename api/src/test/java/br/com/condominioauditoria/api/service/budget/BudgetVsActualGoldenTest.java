package br.com.condominioauditoria.api.service.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualGroupResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualLineResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualWarningResponse;
import br.com.condominioauditoria.api.dto.response.budget.EvidenceResponse;
import br.com.condominioauditoria.api.dto.response.budget.FundResultResponse;
import br.com.condominioauditoria.api.dto.response.budget.OverrunLineResponse;
import br.com.condominioauditoria.api.dto.response.budget.PeriodMappingSummaryResponse;
import br.com.condominioauditoria.api.messaging.GoldenMessages;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.Enrichment;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.LedgerEntryData;
import br.com.condominioauditoria.api.model.accounting.LedgerEntry;
import br.com.condominioauditoria.api.model.enums.BudgetVsActualStatus;
import br.com.condominioauditoria.api.model.enums.FundComparisonStatus;
import br.com.condominioauditoria.api.model.enums.MonthStatus;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.Cumulative;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.Month;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Acceptance case of RF-03.1.15: September/2026 of the pilot, with the real budget and cash flow (v2 messages of the
 * private golden), the mapping of the 73 accounts confirmed and the funds linked. Each expense line of
 * previsto-realizado-2026-09.csv is checked cent by cent. Skipped without data/golden/privado.
 */
class BudgetVsActualGoldenTest {

    @Test
    void septemberOperatingFund() {
        SeptemberGolden g = golden();
        g.confirmMap();

        BudgetVsActualResponse r = g.september().result();

        assertThat(r.status()).isEqualTo(BudgetVsActualStatus.CALCULADO);
        assertThat(r.calculationVersion()).isEqualTo(BudgetVsActualCalculator.VERSION);
        // RF-03.1.6
        assertThat(r.cashFlowCheck().fundDebits()).isEqualByComparingTo("449455.13");
        assertThat(r.cashFlowCheck().matches()).isTrue();
        assertThat(r.adjustments().total()).isEqualByComparingTo("3278.24");
        assertThat(r.adjustments().accounts()).extracting(c -> c.account() + "=" + c.amount().toPlainString())
                .containsExactly("1324=3987.12", "1327=-708.88");
        assertThat(r.toReallocate().total()).isEqualByComparingTo("1050.93");
        assertThat(r.toReallocate().accounts()).singleElement().satisfies(c -> assertThat(c.account()).isEqualTo("1064"));
        assertThat(r.totals().actualExpense()).isEqualByComparingTo("446176.89");
        assertThat(r.totals().inLines()).isEqualByComparingTo("445125.96");
        assertThat(r.totals().planned()).isEqualByComparingTo("451620.13");
        assertThat(r.totals().monthlyPlanned()).isEqualByComparingTo("451620.13");
        assertThat(r.totals().difference()).isEqualByComparingTo("-5443.24");
        assertThat(r.totals().execution()).isEqualByComparingTo("98.8");
        assertThat(r.withoutBudgetLine().total()).isEqualByComparingTo("0.00");
        assertThat(r.withoutBudgetLine().entries()).isZero();
        assertThat(r.mapping()).isEqualTo(new PeriodMappingSummaryResponse(73, 73, 0));
        assertThat(r.provisional()).isTrue();

        // Groups (planned; actual; difference). Contracts by the sum of the lines (Q30): 336.274,18
        assertThat(groups(r)).containsExactly(
                "1.1 69193.86 62815.41 -6378.45", "1.2 694.05 754.94 60.89", "1.3 336274.18 341277.13 5002.95",
                "1.4 0.00 0.00 0.00", "1.5 2850.00 8958.32 6108.32", "1.6 17388.04 14264.32 -3123.72",
                "1.7 15200.00 16910.40 1710.40", "1.8 10020.00 145.44 -9874.56");

        // RF-03.1.8 and RF-03.1.12
        BudgetVsActualLineResponse manager = line(r, "1.3.20");
        assertThat(List.of(manager.planned(), manager.actual(), manager.difference()))
                .usingElementComparator(BigDecimal::compareTo).containsExactly(dec("8000.00"), dec("7120.00"),
                        dec("-880.00"));
        var managerEvidence = g.september().evidence().get(BudgetVsActualCalculator.lineTarget(manager.lineId()));
        assertThat(managerEvidence).singleElement().satisfies(ev -> {
            assertThat(ev.account()).isEqualTo("1108");
            assertThat(ev.date()).isEqualTo(LocalDate.of(2026, 9, 9));
            assertThat(ev.page()).isPositive();
            assertThat(ev.fileId()).isEqualTo(g.cashFlowFileId);
            assertThat(ev.sha256()).hasSize(64);
        });
        BudgetVsActualLineResponse concierge = line(r, "1.3.10");
        assertThat(concierge.actual()).isEqualByComparingTo("86816.34");
        assertThat(concierge.execution()).isEqualByComparingTo("108.5");
        var concierageEvidence = g.september().evidence().get(BudgetVsActualCalculator.lineTarget(concierge.lineId()));
        assertThat(concierageEvidence).allMatch(ev -> ev.account().equals("1442"));
        assertThat(concierageEvidence.stream().map(EvidenceResponse::amount).reduce(BigDecimal.ZERO,
                BigDecimal::add)).isEqualByComparingTo("86816.34");
        assertThat(line(r, "1.1.1").difference()).isEqualByComparingTo("-9556.62");
        assertThat(line(r, "1.1.5").actual()).isEqualByComparingTo("0.00");
        assertThat(line(r, "1.3.23").actual()).isEqualByComparingTo("0.00");
    }

    @Test
    void eachCsvLineToTheCent() {
        SeptemberGolden g = golden();
        Optional<String> csv = GoldenMessages.text("previsto-realizado-2026-09.csv");
        assumeTrue(csv.isPresent(), "previsto-realizado-2026-09.csv ausente no golden privado");
        g.confirmMap();
        BudgetVsActualResponse r = g.september().result();
        Map<String, BudgetVsActualLineResponse> byCode = r.groups().stream().flatMap(gr -> gr.lines().stream())
                .collect(Collectors.toMap(BudgetVsActualLineResponse::code, l -> l));

        int checked = 0;
        Map<String, String[]> special = new HashMap<>();
        for (String line : csv.get().lines().skip(1).toList()) {
            String[] c = line.split(";", -1);
            String code = c[0];
            if (!code.matches("\\d+(\\.\\d+)+")) {
                special.put(code, c);
                continue;
            }
            if (code.startsWith("1.9.")) {
                continue; // continue; // funds: compared by collection (RF-03.1.9), in the funds test
            }
            BudgetVsActualLineResponse l = byCode.get(code);
            assertThat(l).as("linha %s existe no resultado", code).isNotNull();
            assertThat(l.planned()).as("previsto %s", code).isEqualByComparingTo(brazilianAmount(c[2]));
            assertThat(l.actual()).as("realizado %s", code).isEqualByComparingTo(brazilianAmount(c[3]));
            assertThat(l.difference()).as("diferença %s", code).isEqualByComparingTo(brazilianAmount(c[4]));
            List<String> accounts = c[5].isBlank() ? List.of() : Arrays.asList(c[5].trim().split(" "));
            assertThat(l.cashFlowAccounts()).as("contas do fluxo %s",
                    code).containsExactlyInAnyOrderElementsOf(accounts);
            checked++;
        }
        assertThat(checked).isEqualTo(70);
        // Budget lines the CSV does not list: planned and actual zero
        byCode.values().stream().filter(l -> csv.get().lines().noneMatch(x -> x.startsWith(l.code() + ";")))
                .forEach(l -> {
                    assertThat(l.planned()).as("previsto %s fora do CSV", l.code()).isEqualByComparingTo("0");
                    assertThat(l.actual()).as("realizado %s fora do CSV", l.code()).isEqualByComparingTo("0");
                });
        // Separate blocks of the CSV: adjustments and to reallocate
        assertThat(brazilianAmount(special.get("AJUSTE (estorno)")[3]).add(brazilianAmount(special.get("AJUSTE (repasse)")[3])))
                .isEqualByComparingTo(r.adjustments().total());
        assertThat(brazilianAmount(special.get("REALOCAR (cartão)")[3])).isEqualByComparingTo(r.toReallocate().total());
    }

    @Test
    void rule20InSeptember() {
        SeptemberGolden g = golden();
        g.confirmMap();

        var rule = g.september().result().rule20();

        assertThat(rule.overrun()).isEqualByComparingTo("38880.19");
        assertThat(rule.linesAbove()).isEqualTo(25);
        assertThat(rule.percentage()).isEqualByComparingTo("8.6");
        assertThat(rule.monthlyPlanned()).isEqualByComparingTo("451620.13");
        assertThat(rule.limit()).isEqualByComparingTo("90324.03");
        assertThat(rule.aboveLimit()).isFalse();
        assertThat(rule.toReallocate()).isEqualByComparingTo("1050.93");
        assertThat(rule.maxScenario()).isEqualByComparingTo("39931.12");
        assertThat(rule.maxScenarioPercentage()).isEqualByComparingTo("8.8");
        assertThat(rule.provisional()).isTrue();
        assertThat(rule.lines().stream().map(OverrunLineResponse::overrun).reduce(BigDecimal.ZERO,
                BigDecimal::add)).isEqualByComparingTo("38880.19");
    }

    @Test
    void reserveAndWorksFundsByCollection() {
        SeptemberGolden g = golden();
        g.confirmMap();

        List<FundResultResponse> funds = g.september().result().funds();

        FundResultResponse reserve = fund(funds, "FUNDO DE RESERVA");
        assertThat(reserve.status()).isEqualTo(FundComparisonStatus.COMPARADO);
        assertThat(reserve.lineCode()).isEqualTo("1.9.1");
        assertThat(List.of(reserve.planned(), reserve.collected(), reserve.difference(), reserve.execution()))
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(dec("13548.60"), dec("14260.79"), dec("712.19"), dec("105.3"));
        FundResultResponse works = fund(funds, "OBRAS / REFORMAS / INFRA");
        assertThat(works.lineCode()).isEqualTo("1.9.2");
        assertThat(List.of(works.planned(), works.collected(), works.difference(), works.execution()))
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(dec("9032.40"), dec("9705.06"), dec("672.66"), dec("107.4"));
        FundResultResponse power = fund(funds, "ENERGIA ELETRICA");
        assertThat(power.status()).isEqualTo(FundComparisonStatus.SEM_PREVISTO_NA_PO);
        assertThat(power.difference()).isNull();
        FundResultResponse worksOnly = fund(funds, "OBRAS");
        assertThat(worksOnly.status()).isEqualTo(FundComparisonStatus.SEM_PREVISTO_NA_PO);
        assertThat(worksOnly.debits()).isEqualByComparingTo("25.13");
    }

    @Test
    void cumulativeWithOnlySeptember() {
        SeptemberGolden g = golden();
        g.confirmMap();

        BudgetVsActualResponse r = BudgetVsActualCalculator.calculate(g.input(new Cumulative(),
                List.of(g.septemberCashFlow()),
                List.of())).result();

        assertThat(r.status()).isEqualTo(BudgetVsActualStatus.CALCULADO);
        assertThat(r.totals().planned()).isEqualByComparingTo("451620.13");
        assertThat(r.totals().actualExpense()).isEqualByComparingTo("446176.89");
        assertThat(r.totals().fiscalYearPlanned()).isEqualByComparingTo("5419441.56");
        assertThat(r.summedMonths()).containsExactly("2026-09");
        assertThat(r.monthsWithoutCashFlow()).containsExactly("2026-05", "2026-06", "2026-07", "2026-08");
        assertThat(r.warnings()).extracting(BudgetVsActualWarningResponse::text)
                .contains("mai, jun, jul e ago/2026 sem fluxo carregado");
        assertThat(r.months()).hasSize(12);
        assertThat(r.months().get(4).status()).isEqualTo(MonthStatus.COM_FLUXO);
        assertThat(r.months().get(4).overrun()).isEqualByComparingTo("38880.19");
        assertThat(r.months().get(0).planned()).isNull();
        assertThat(r.rule20()).isNull();
    }

    @Test
    void the73SuggestedAccountsSumNothing() {
        SeptemberGolden g = golden();
        g.scenario.accountMapping.loadSheet(g.scenario.condominiumId, g.budget.getId(), "mapa.csv",
                SeptemberGolden.map()
                .orElseThrow(), "admin");

        BudgetVsActualResponse r = g.september().result();

        assertThat(r.totals().inLines()).isEqualByComparingTo("0.00");
        assertThat(r.groups()).flatMap(BudgetVsActualGroupResponse::lines)
                .allMatch(l -> l.actual().signum() == 0 && l.cashFlowAccounts().isEmpty());
        assertThat(r.withoutBudgetLine().total()).isEqualByComparingTo("449455.13");
        assertThat(r.withoutBudgetLine().entries()).isEqualTo(r.cashFlowCheck().entries());
        assertThat(r.adjustments().total()).isEqualByComparingTo("0.00");
        assertThat(r.toReallocate().total()).isEqualByComparingTo("0.00");
        assertThat(r.mapping()).isEqualTo(new PeriodMappingSummaryResponse(73, 0, 73));
        assertThat(r.warnings()).extracting(BudgetVsActualWarningResponse::text)
                .anyMatch(t -> t.startsWith("73 contas sem de-para confirmado"));
        assertThat(r.provisional()).isTrue();
        assertThat(r.withoutBudgetLine().accounts()).allMatch(c -> c.detail().equals("de-para sugerido"));
    }

    @Test
    void testEntryWithoutMappingStaysWithoutBudgetLine() {
        SeptemberGolden g = golden();
        g.confirmMap();
        var read = new LedgerEntryData(99, 1, LocalDate.of(2026, 9, 30), "8888", "CONTA DE TESTE", "", "Teste",
                BigDecimal.ZERO.setScale(2), new BigDecimal("500.00"), BigDecimal.ZERO.setScale(2),
                new Enrichment(null, null, null, false, false));
        g.scenario.ledgerEntries.add(new LedgerEntry(g.scenario.condominiumId, g.cashFlowFileId,
                g.scenario.operatingFund.getId(),
                read));

        BudgetVsActualResponse r = g.september().result();

        assertThat(r.withoutBudgetLine().total()).isEqualByComparingTo("500.00");
        assertThat(r.withoutBudgetLine().accounts()).singleElement().satisfies(c -> {
            assertThat(c.account()).isEqualTo("8888");
            assertThat(c.detail()).isEqualTo("sem de-para");
        });
        assertThat(r.totals().actualExpense()).isEqualByComparingTo("446676.89");
        assertThat(r.totals().inLines()).isEqualByComparingTo("445125.96");
        assertThat(r.rule20().overrun()).isEqualByComparingTo("38880.19");
        assertThat(r.warnings()).extracting(BudgetVsActualWarningResponse::text)
                .anyMatch(t -> t.startsWith("1 conta sem de-para confirmado"));
    }

    @Test
    void reallocationOfCardPurchasesTo179() {
        SeptemberGolden g = golden();
        g.confirmMap();
        List<BudgetVsActualCalculator.ReallocatedEntry> reallocations = g.scenario.ledgerEntries.stream()
                .filter(l -> "1064".equals(l.getAccountCode()) && l.getDebit().signum() != 0)
                .map(l -> new BudgetVsActualCalculator.ReallocatedEntry(UUID.randomUUID(),
                        br.com.condominioauditoria.api.model.accounting.LedgerEntryFingerprint.key(l), l.getFileId(),
                        l.getDate(), l.getAccountCode(), l.getDebit(), l.getPage(), g.line("1.7.9").getId(),
                        "gestor", Instant.EPOCH))
                .toList();

        BudgetVsActualResponse r = BudgetVsActualCalculator.calculate(g.input(new Month(YearMonth.of(2026, 9)),
                List.of(g.septemberCashFlow()), reallocations)).result();

        assertThat(line(r, "1.7.9").actual()).isEqualByComparingTo("5522.25");
        assertThat(line(r, "1.7.9").difference()).isEqualByComparingTo("3222.25");
        assertThat(r.toReallocate().total()).isEqualByComparingTo("0.00");
        assertThat(r.totals().actualExpense()).isEqualByComparingTo("446176.89");
    }

    @Test
    void twoCashFlowsInMonthShowNoNumber() {
        SeptemberGolden g = golden();
        g.confirmMap();
        var copy = new BudgetVsActualCalculator.CashFlowFile(UUID.randomUUID(), "fluxo-corrigido.pdf", "e".repeat(64),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), Instant.EPOCH.plusSeconds(60), "gestor");

        BudgetVsActualResponse month = BudgetVsActualCalculator.calculate(g.input(new Month(YearMonth.of(2026, 9)),
                List.of(g.septemberCashFlow(), copy), List.of())).result();
        BudgetVsActualResponse cumulative = BudgetVsActualCalculator.calculate(g.input(new Cumulative(),
                List.of(g.septemberCashFlow(), copy), List.of())).result();

        assertThat(month.status()).isEqualTo(BudgetVsActualStatus.DOIS_FLUXOS);
        assertThat(month.message()).isEqualTo("Dois fluxos para 09/2026: substitua, reclassifique ou exclua um");
        assertThat(month.totals()).isNull();
        assertThat(month.groups()).isEmpty();
        assertThat(month.months().getFirst().cashFlows()).hasSize(2);
        assertThat(cumulative.status()).isEqualTo(BudgetVsActualStatus.DOIS_FLUXOS);
        assertThat(cumulative.monthsWithTwoCashFlows()).containsExactly("2026-09");
    }

    @Test
    void sameInputSameResult() {
        SeptemberGolden g = golden();
        g.confirmMap();

        assertThat(g.september()).isEqualTo(g.september());
    }

    private static SeptemberGolden golden() {
        Optional<SeptemberGolden> g = SeptemberGolden.load();
        assumeTrue(g.isPresent() && SeptemberGolden.map().isPresent(), "golden privado ausente");
        return g.get();
    }

    private static List<String> groups(BudgetVsActualResponse r) {
        return r.groups().stream().map(gr -> gr.code() + " " + gr.planned().toPlainString() + " "
                + gr.actual().toPlainString() + " " + gr.difference().toPlainString()).toList();
    }

    private static BudgetVsActualLineResponse line(BudgetVsActualResponse r, String code) {
        return r.groups().stream().flatMap(g -> g.lines().stream()).filter(l -> l.code().equals(code)).findFirst()
                .orElseThrow();
    }

    private static FundResultResponse fund(List<FundResultResponse> funds, String name) {
        return funds.stream().filter(f -> name.equals(f.fund())).findFirst().orElseThrow();
    }

    private static BigDecimal brazilianAmount(String text) {
        return new BigDecimal(text.trim().replace(".", "").replace(',', '.'));
    }

    private static BigDecimal dec(String v) {
        return new BigDecimal(v);
    }
}
