package br.com.condominioauditoria.api.service.calculator;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualWarningResponse;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.Enrichment;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.LedgerEntryData;
import br.com.condominioauditoria.api.model.accounting.LedgerEntry;
import br.com.condominioauditoria.api.model.budget.AccountMapping;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.budget.MappingTarget;
import br.com.condominioauditoria.api.model.enums.AccountMappingSource;
import br.com.condominioauditoria.api.model.enums.AccountMappingStatus;
import br.com.condominioauditoria.api.model.enums.BudgetLineType;
import br.com.condominioauditoria.api.model.enums.BudgetStatus;
import br.com.condominioauditoria.api.model.enums.BudgetVsActualStatus;
import br.com.condominioauditoria.api.model.enums.FundComparisonStatus;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.CashFlowFile;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.Cumulative;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.Input;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.Month;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.Period;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** RF-03.1.6 to RF-03.1.11 with synthetic data (without the golden): edges and situations without a number. */
class BudgetVsActualCalculatorTest {

    private static final UUID CONDOMINIUM = UUID.randomUUID();
    private static final UUID OPERATING = UUID.randomUUID();
    private static final UUID RESERVE = UUID.randomUUID();
    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);

    private final List<LedgerEntry> entries = new ArrayList<>();
    private final UUID septemberFile = UUID.randomUUID();
    private final UUID octoberFile = UUID.randomUUID();

    @Test
    void exactly20PercentDoesNotExceedAndOneCentMoreExceeds() {
        // Planned 451.620,10; overrun 90.324,02 = exactly 20% → no finding ("não ultrapassem 20%")
        TestBudget budget = simpleBudget("451620.10");
        debit(budget, septemberFile, "1001", "541944.12", LocalDate.of(2026, 9, 5));

        var r = calculate(budget, new Month(SEPTEMBER), List.of(cashFlow(septemberFile, SEPTEMBER))).rule20();

        assertThat(r.overrun()).isEqualByComparingTo("90324.02");
        assertThat(r.limit()).isEqualByComparingTo("90324.02");
        assertThat(r.percentage()).isEqualByComparingTo("20.0");
        assertThat(r.aboveLimit()).isFalse();

        entries.clear();
        debit(budget, septemberFile, "1001", "541944.13", LocalDate.of(2026, 9, 5));
        var above = calculate(budget, new Month(SEPTEMBER), List.of(cashFlow(septemberFile, SEPTEMBER))).rule20();
        assertThat(above.overrun()).isEqualByComparingTo("90324.03");
        assertThat(above.aboveLimit()).isTrue();
        assertThat(above.provisional()).isFalse();
    }

    @Test
    void monthWithoutCashFlowNeverBecomesZero() {
        TestBudget budget = simpleBudget("1000.00");

        var r = calculate(budget, new Month(SEPTEMBER), List.of());

        assertThat(r.status()).isEqualTo(BudgetVsActualStatus.SEM_FLUXO);
        assertThat(r.message()).isEqualTo("Sem fluxo carregado para 09/2026");
        assertThat(r.totals()).isNull();
        assertThat(r.groups()).isEmpty();
    }

    @Test
    void outsideFiscalYearUnconfirmedBudgetAndNoOperatingFund() {
        TestBudget budget = simpleBudget("1000.00");
        assertThat(calculate(budget, new Month(YearMonth.of(2026, 4)), List.of()).message())
                .isEqualTo("Sem PO aprovada para 04/2026");
        assertThat(BudgetVsActualCalculator.calculate(input(null, new Month(SEPTEMBER), List.of(), OPERATING)).result()
                .status()).isEqualTo(BudgetVsActualStatus.SEM_PO);

        Budget read = new Budget(CONDOMINIUM, UUID.randomUUID(), "a".repeat(64));
        read.recordReading("po-protest", "PO", null, null, null, BudgetStatus.LIDA, null, null, BigDecimal.ONE,
                new BigDecimal("0.01"), Instant.EPOCH);
        var unconfirmed = BudgetVsActualCalculator.calculate(new Input(read, null, budget.lines, List.of(), Map.of(),
                Map.of(), OPERATING, List.of(), List.of(), List.of(), null, List.of(), new Month(SEPTEMBER))).result();
        assertThat(unconfirmed.status()).isEqualTo(BudgetVsActualStatus.PO_NAO_CONFIRMADA);
        assertThat(unconfirmed.totals()).isNull();

        var withoutOperatingFund = BudgetVsActualCalculator.calculate(input(budget, new Month(SEPTEMBER),
                List.of(cashFlow(septemberFile, SEPTEMBER)),
                null)).result();
        assertThat(withoutOperatingFund.status()).isEqualTo(BudgetVsActualStatus.SEM_FUNDO_ORDINARIO);
    }

    @Test
    void interFundTransferStaysOutAndCheckMatches() {
        TestBudget budget = simpleBudget("1000.00");
        debit(budget, septemberFile, "1001", "800.00", LocalDate.of(2026, 9, 5));
        LedgerEntry transfer = entry(septemberFile, OPERATING, "2133", "0.00", "50.00", true, false,
                LocalDate.of(2026, 9, 6));
        entries.add(transfer);
        debit(budget, septemberFile, null, "30.00", LocalDate.of(2026, 9, 7));

        var r = calculate(budget, new Month(SEPTEMBER), List.of(cashFlow(septemberFile, SEPTEMBER)));

        assertThat(r.totals().inLines()).isEqualByComparingTo("800.00");
        assertThat(r.withoutBudgetLine().total()).isEqualByComparingTo("30.00");
        assertThat(r.withoutBudgetLine().accounts()).singleElement().satisfies(c -> assertThat(c.detail())
                .isEqualTo("lançamento sem conta"));
        assertThat(r.totals().actualExpense()).isEqualByComparingTo("830.00");
        assertThat(r.cashFlowCheck().fundDebits()).isEqualByComparingTo("880.00");
        assertThat(r.cashFlowCheck().transfers()).isEqualByComparingTo("50.00");
        assertThat(r.cashFlowCheck().matches()).isTrue();
        assertThat(r.totals().execution()).isEqualByComparingTo("83.0");
    }

    @Test
    void entryFromAnotherMonthOrFileIsLeftOut() {
        TestBudget budget = simpleBudget("1000.00");
        debit(budget, septemberFile, "1001", "100.00", LocalDate.of(2026, 9, 30));
        debit(budget, septemberFile, "1001", "200.00", LocalDate.of(2026, 10, 1));
        debit(budget, UUID.randomUUID(), "1001", "400.00", LocalDate.of(2026, 9, 15));

        var r = calculate(budget, new Month(SEPTEMBER), List.of(cashFlow(septemberFile, SEPTEMBER)));

        assertThat(r.totals().actualExpense()).isEqualByComparingTo("100.00");
    }

    @Test
    void cumulativeSumsOnlyMonthsWithCashFlow() {
        TestBudget budget = simpleBudget("1000.00");
        debit(budget, septemberFile, "1001", "900.00", LocalDate.of(2026, 9, 10));
        debit(budget, octoberFile, "1001", "1300.00", LocalDate.of(2026, 10, 10));

        var r = calculate(budget, new Cumulative(), List.of(cashFlow(septemberFile, SEPTEMBER), cashFlow(octoberFile,
                SEPTEMBER.plusMonths(1))));

        assertThat(r.summedMonths()).containsExactly("2026-09", "2026-10");
        assertThat(r.monthsWithoutCashFlow()).containsExactly("2026-05", "2026-06", "2026-07", "2026-08");
        assertThat(r.totals().planned()).isEqualByComparingTo("2000.00");
        assertThat(r.totals().actualExpense()).isEqualByComparingTo("2200.00");
        assertThat(r.totals().fiscalYearPlanned()).isEqualByComparingTo("12000.00");
        assertThat(r.months()).hasSize(12);
        assertThat(r.months().get(5).overrun()).isEqualByComparingTo("300.00");
        assertThat(r.rule20()).isNull();
    }

    @Test
    void fundsWithoutSavedFeeReceiptAskForReprocessingAndLineWithoutFundHasNoNumber() throws Exception {
        TestBudget budget = budgetWithFund();
        LedgerEntry fee = entry(septemberFile, RESERVE, null, "300.00", "0.00", false, true, LocalDate.of(2026, 9,
                2));
        LedgerEntry income = entry(septemberFile, RESERVE, null, "100.00", "0.00", false, false,
                LocalDate.of(2026, 9, 3));
        entries.addAll(List.of(fee, income));

        var r = calculate(budget, new Month(SEPTEMBER), List.of(cashFlow(septemberFile, SEPTEMBER)));
        var reserve = r.funds().stream().filter(f -> RESERVE.equals(f.fundId())).findFirst().orElseThrow();
        assertThat(reserve.status()).isEqualTo(FundComparisonStatus.COMPARADO);
        assertThat(reserve.collected()).isEqualByComparingTo("300.00");
        assertThat(reserve.credits()).isEqualByComparingTo("400.00");
        assertThat(r.funds()).anySatisfy(f -> {
            assertThat(f.status()).isEqualTo(FundComparisonStatus.LINHA_SEM_FUNDO);
            assertThat(f.lineCode()).isEqualTo("1.9.2");
            assertThat(f.collected()).isNull();
        });
        assertThat(r.warnings()).extracting(BudgetVsActualWarningResponse::text).contains("linha 1.9.2 sem fundo ligado");

        // Cash flow stored before the recebimento_cota column (V9): never zero, asks for reprocessing
        var field = LedgerEntry.class.getDeclaredField("condoFeeReceipt");
        field.setAccessible(true);
        field.set(fee, null);
        var old = calculate(budget, new Month(SEPTEMBER), List.of(cashFlow(septemberFile, SEPTEMBER)));
        assertThat(old.funds()).anySatisfy(f -> {
            assertThat(f.status()).isEqualTo(FundComparisonStatus.REPROCESSAR_FLUXO);
            assertThat(f.collected()).isNull();
        });
    }

    @Test
    void percentageWithZeroPlannedIsEmptyAndMonthList() {
        assertThat(BudgetVsActualCalculator.percentage(new BigDecimal("10.00"), BigDecimal.ZERO)).isNull();
        assertThat(BudgetVsActualCalculator.percentage(new BigDecimal("38880.19"), new BigDecimal("451620.13")))
                .isEqualByComparingTo("8.6");
        assertThat(BudgetVsActualCalculator.monthList(List.of("2026-05", "2026-06", "2026-07", "2026-08")))
                .isEqualTo("mai, jun, jul e ago/2026");
        assertThat(BudgetVsActualCalculator.monthList(List.of("2026-11", "2026-12", "2027-01")))
                .isEqualTo("nov e dez/2026, jan/2027");
    }

    // ---- setup

    /**
     * Budget confirmed 05/2026 to 04/2027 with a group of one line (1.1.1) and account mapping 1001 → 1.1.1 confirmed.
     */
    private record TestBudget(Budget budget, List<BudgetLine> lines, List<AccountMapping> mappings,
            Map<UUID, UUID> fundByLine) {
    }

    private static TestBudget simpleBudget(String budgeted) {
        Budget p = confirmed();
        BudgetLine total = line(p, 1, BudgetLineType.TOTAL, "1", null, "TOTAL", budgeted);
        BudgetLine group = line(p, 2, BudgetLineType.GRUPO, "1.1", null, "PESSOAL", budgeted);
        BudgetLine l = line(p, 3, BudgetLineType.LINHA, "1.1.1", "1545 - Salários", "Salários", budgeted);
        AccountMapping d = new AccountMapping(p, "1001", "SALARIO", MappingTarget.line(l),
                AccountMappingStatus.CONFIRMADO,
                AccountMappingSource.ADMIN, null, false, "admin", Instant.EPOCH);
        return new TestBudget(p, List.of(total, group, l), List.of(d), Map.of());
    }

    private static TestBudget budgetWithFund() {
        Budget p = confirmed();
        BudgetLine group = line(p, 1, BudgetLineType.GRUPO, "1.1", null, "PESSOAL", "1000.00");
        BudgetLine l = line(p, 2, BudgetLineType.LINHA, "1.1.1", "1545 - Salários", "Salários", "1000.00");
        BudgetLine funds = new BudgetLine(p, 3, 1, BudgetLineType.GRUPO, "1.9", null, "Fundos", null,
                "Fundos do Condomínio",
                BigDecimal.ZERO.setScale(2), new BigDecimal("50.00"), null, null);
        BudgetLine reserve = line(p, 4, BudgetLineType.LINHA, "1.9.1", null, "Fundo de Reserva", "30.00");
        BudgetLine works = line(p, 5, BudgetLineType.LINHA, "1.9.2", null, "Fundo de Obras", "20.00");
        return new TestBudget(p, List.of(group, l, funds, reserve, works), List.of(), Map.of(reserve.getId(), RESERVE));
    }

    private static Budget confirmed() {
        Budget p = new Budget(CONDOMINIUM, UUID.randomUUID(), "a".repeat(64));
        p.recordReading("po-protest", "PO", null, null, null, BudgetStatus.LIDA, null, null, BigDecimal.ONE,
                new BigDecimal("0.01"), Instant.EPOCH);
        p.confirm(1, YearMonth.of(2026, 5), YearMonth.of(2027, 4), null, true, null, false, null, "admin",
                Instant.EPOCH);
        return p;
    }

    private static BudgetLine line(Budget p, int position, BudgetLineType type, String code, String account,
            String description, String budgeted) {
        return new BudgetLine(p, position, 1, type, code, account, null, null, description, BigDecimal.ZERO.setScale(2),
                new BigDecimal(budgeted), null, null);
    }

    private void debit(TestBudget budget, UUID file, String account, String amount, LocalDate date) {
        entries.add(entry(file, OPERATING, account, "0.00", amount, false, false, date));
    }

    private LedgerEntry entry(UUID file, UUID fund, String account, String credit, String debit,
            boolean transfer, boolean fee, LocalDate date) {
        var read = new LedgerEntryData(1, entries.size() + 1, date, account, account == null ? "" : "CONTA " + account,
                "",
                "Teste", new BigDecimal(credit), new BigDecimal(debit), BigDecimal.ZERO.setScale(2),
                new Enrichment(null, null, null, transfer, fee));
        return new LedgerEntry(CONDOMINIUM, file, fund, read);
    }

    private static CashFlowFile cashFlow(UUID file, YearMonth month) {
        return new CashFlowFile(file, "fluxo-" + month + ".pdf", "f".repeat(64), month.atDay(1), month.atEndOfMonth(),
                Instant.EPOCH, "gestor");
    }

    private BudgetVsActualResponse calculate(TestBudget budget, Period period, List<CashFlowFile> cashFlows) {
        return BudgetVsActualCalculator.calculate(input(budget, period, cashFlows, OPERATING)).result();
    }

    private Input input(TestBudget budget, Period period, List<CashFlowFile> cashFlows, UUID operatingFund) {
        if (budget == null) {
            return new Input(null, null, null, null, null, null, operatingFund, cashFlows, entries, null, null, null,
                    period);
        }
        return new Input(budget.budget(), "po.pdf", budget.lines(), budget.mappings(), budget.fundByLine(),
                Map.of(OPERATING, "CONDOMÍNIO", RESERVE, "FUNDO DE RESERVA"), operatingFund, cashFlows, entries,
                        List.of(),
                new BigDecimal("20.0000"), List.of(), period);
    }
}
