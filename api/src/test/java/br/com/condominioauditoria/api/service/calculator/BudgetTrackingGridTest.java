package br.com.condominioauditoria.api.service.calculator;

import static br.com.condominioauditoria.api.service.calculator.SyntheticSeptember.OCTOBER;
import static br.com.condominioauditoria.api.service.calculator.SyntheticSeptember.OPERATING;
import static br.com.condominioauditoria.api.service.calculator.SyntheticSeptember.SEPTEMBER;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.api.dto.response.budget.BudgetTrackingResponse;
import br.com.condominioauditoria.api.dto.response.budget.TrackingCellResponse;
import br.com.condominioauditoria.api.dto.response.budget.TrackingGroupResponse;
import br.com.condominioauditoria.api.dto.response.budget.TrackingLineResponse;
import br.com.condominioauditoria.api.dto.response.budget.TrackingMonthResponse;
import br.com.condominioauditoria.api.model.enums.BudgetVsActualStatus;
import br.com.condominioauditoria.api.model.enums.MonthStatus;
import br.com.condominioauditoria.api.model.enums.SpendingProfile;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.Month;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** RF-13.4 and RF-13.10: the tracking grid with the synthetic September/2026 (numbers of the requirement). */
class BudgetTrackingGridTest {

    private final SyntheticSeptember s = new SyntheticSeptember();

    @Test
    void septemberHasTheNumbersOfBudgetVsActualAndTheOtherMonthsHaveNone() {
        BudgetTrackingResponse r = s.grid(s.confirmedProfiles()).result();

        assertThat(r.status()).isEqualTo(BudgetVsActualStatus.CALCULATED);
        assertThat(r.calculationVersion()).isEqualTo(BudgetTrackingGrid.VERSION);
        assertThat(r.budgetVsActualVersion()).isEqualTo(BudgetVsActualCalculator.VERSION);
        assertThat(r.months()).hasSize(12);
        assertThat(r.months()).filteredOn(m -> m.status() == MonthStatus.NO_CASH_FLOW).hasSize(11)
                .allSatisfy(m -> {
                    assertThat(m.planned()).isNull();
                    assertThat(m.actualInLines()).isNull();
                });
        TrackingMonthResponse sep = month(r, "2026-09");
        assertThat(sep.inCumulative()).isTrue();
        assertThat(sep.planned()).isEqualByComparingTo("451620.13");
        assertThat(sep.actualInLines()).isEqualByComparingTo("445125.96");
        assertThat(sep.overrun()).isEqualByComparingTo("38880.19");
        assertThat(sep.linesAbove()).isEqualTo(25);
        assertThat(sep.overrunPercentage()).isEqualByComparingTo("8.6");
        assertThat(sep.aboveLimit()).isFalse();

        TrackingLineResponse salaries = line(r, "1.1.1");
        TrackingCellResponse cell = cell(salaries, "2026-09");
        assertThat(cell.actual()).isEqualByComparingTo("26377.53");
        assertThat(cell.difference()).isEqualByComparingTo("-9556.62");
        assertThat(cell(salaries, "2026-08").actual()).isNull();
        assertThat(salaries.balance()).isEqualByComparingTo("9556.62");
        TrackingLineResponse concierge = line(r, "1.3.10");
        assertThat(cell(concierge, "2026-09").actual()).isEqualByComparingTo("86816.34");
        assertThat(cell(concierge, "2026-09").difference()).isEqualByComparingTo("6793.38");
        assertThat(concierge.balance()).isEqualByComparingTo("-6793.38");
        assertThat(concierge.monthsAbove()).isEqualTo(1);

        // Filter "só linhas com estouro no mês": 25 lines summing 38.880,19 (the excess of RF-03.1.11)
        List<TrackingLineResponse> above = r.groups().stream().flatMap(g -> g.lines().stream())
                .filter(l -> cell(l, "2026-09").difference() != null && cell(l, "2026-09").difference().signum() > 0)
                .toList();
        assertThat(above).hasSize(25);
        assertThat(above.stream().map(l -> cell(l, "2026-09").difference()).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("38880.19");

        assertThat(r.cumulative().months()).isEqualTo(1);
        assertThat(r.cumulative().planned()).isEqualByComparingTo("451620.13");
        assertThat(r.cumulative().actualInLines()).isEqualByComparingTo("445125.96");
        assertThat(r.cumulative().balance()).isEqualByComparingTo("6494.17");
        assertThat(r.blocks().adjustments().cumulative()).isEqualByComparingTo("3278.24");
        assertThat(r.blocks().toReallocate().cumulative()).isEqualByComparingTo("1050.93");
        assertThat(r.blocks().withoutBudgetLine().cumulative()).isEqualByComparingTo("0.00");
        assertThat(line(r, "1.1.5").effectiveProfile()).isEqualTo(SpendingProfile.PROVISION);
        // RF-13.14 (rules v2): "anual" only by the word in Observações; "média 3 assembléias no ano" stays recurring
        // until the Admin confirms (P9 proposes eventual)
        assertThat(line(r, "1.6.20").effectiveProfile()).isEqualTo(SpendingProfile.RECURRING);
    }

    @Test
    void eachCellIsTheBudgetVsActualOfTheMonth() {
        var grid = s.grid(s.confirmedProfiles());
        var calc = BudgetVsActualCalculator.calculate(BudgetTrackingGrid.withPeriod(s.base(), new Month(SEPTEMBER)))
                .result();
        calc.groups().stream().flatMap(g -> g.lines().stream()).forEach(l -> {
            TrackingCellResponse c = grid.result().groups().stream().flatMap(g -> g.lines().stream())
                    .filter(x -> x.lineId().equals(l.lineId())).findFirst().map(x -> cell(x, "2026-09")).orElseThrow();
            assertThat(c.planned()).isEqualByComparingTo(l.planned());
            assertThat(c.actual()).isEqualByComparingTo(l.actual());
            assertThat(c.difference()).isEqualByComparingTo(l.difference());
        });
    }

    @Test
    void withoutConfirmationEveryLineIsMonthlyAndThePlannedAmountDoesNotChange() {
        BudgetTrackingResponse confirmed = s.grid(s.confirmedProfiles()).result();
        BudgetTrackingResponse unconfirmed = s.grid(Map.of()).result();

        assertThat(unconfirmed.groups().stream().flatMap(g -> g.lines().stream()))
                .allSatisfy(l -> assertThat(l.effectiveProfile()).isEqualTo(SpendingProfile.RECURRING));
        assertThat(unconfirmed.cumulative()).isEqualTo(confirmed.cumulative());
        assertThat(unconfirmed.warnings()).anySatisfy(w -> assertThat(w.code()).isEqualTo("PROFILE_NOT_CONFIRMED"));
    }

    @Test
    void zeroPlannedLineWithAnyActualIsAFullOverrun() {
        // 1.8.8 Limpeza de caixa d'água: zero planned, a test entry of 500,00 in the month
        var l = s.line("1.8.8");
        s.entries.add(s.entry(s.septemberFile, OPERATING, s.account("1.8.8"), "0.00", "500.00", false, false,
                SEPTEMBER.atDay(15)));

        var r = s.grid(s.confirmedProfiles()).result();

        TrackingCellResponse c = cell(r.groups().stream().flatMap(g -> g.lines().stream())
                .filter(x -> x.lineId().equals(l.getId())).findFirst().orElseThrow(), "2026-09");
        assertThat(c.actual()).isEqualByComparingTo("500.00");
        assertThat(c.difference()).isEqualByComparingTo("500.00");
        assertThat(month(r, "2026-09").overrun()).isEqualByComparingTo("39380.19");
    }

    @Test
    void partialMonthIsShownButOutOfTheCumulativeAndTheRule() {
        UUID october = UUID.randomUUID();
        s.cashFlows.add(SyntheticSeptember.cashFlow(october, OCTOBER, OCTOBER.atEndOfMonth()));
        s.partialUntil.put(october, LocalDate.of(2026, 10, 20));
        String salaries = s.account("1.1.1");
        s.entries.add(s.entry(october, OPERATING, salaries, "0.00", "90000.00", false, false, OCTOBER.atDay(10)));

        BudgetTrackingResponse r = s.grid(s.confirmedProfiles()).result();

        TrackingMonthResponse oct = month(r, "2026-10");
        assertThat(oct.status()).isEqualTo(MonthStatus.WITH_CASH_FLOW);
        assertThat(oct.partialUntil()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(oct.inCumulative()).isFalse();
        assertThat(oct.actualInLines()).isEqualByComparingTo("90000.00");
        // The 20% rule is not assessed in the partial month
        assertThat(oct.overrunPercentage()).isNull();
        assertThat(oct.aboveLimit()).isNull();
        assertThat(cell(line(r, "1.1.1"), "2026-10").actual()).isEqualByComparingTo("90000.00");
        // Cumulative and line balance: only September
        assertThat(r.cumulativeMonths()).containsExactly("2026-09");
        assertThat(r.cumulative().actualInLines()).isEqualByComparingTo("445125.96");
        assertThat(line(r, "1.1.1").balance()).isEqualByComparingTo("9556.62");
        assertThat(r.warnings()).anySatisfy(w -> assertThat(w.text()).startsWith("10/2026 parcial até 20/10"));
    }

    @Test
    void monthInProgressShowsOnlyTheActual() {
        // Q50, with the numbers of RF-13.4: October/2026 up to 20/10
        UUID october = UUID.randomUUID();
        s.cashFlows.add(SyntheticSeptember.cashFlow(october, OCTOBER, OCTOBER.atEndOfMonth()));
        s.partialUntil.put(october, LocalDate.of(2026, 10, 20));
        s.entries.add(s.entry(october, OPERATING, s.account("1.3.10"), "0.00", "86816.34", false, false,
                OCTOBER.atDay(10)));
        s.entries.add(s.entry(october, OPERATING, s.account("1.1.1"), "0.00", "15671.02", false, false,
                OCTOBER.atDay(10)));
        s.entries.add(s.entry(october, OPERATING, s.account("1.3.1"), "0.00", "266718.50", false, false,
                OCTOBER.atDay(10)));

        BudgetTrackingResponse r = s.grid(s.confirmedProfiles()).result();

        TrackingMonthResponse oct = month(r, "2026-10");
        assertThat(oct.actualInLines()).isEqualByComparingTo("369205.86");
        assertThat(oct.difference()).isNull();
        assertThat(oct.overrun()).isNull();
        assertThat(oct.linesAbove()).isNull();
        // The planned amount of the month is a budget figure: it stays
        assertThat(oct.planned()).isEqualByComparingTo("451620.13");
        TrackingCellResponse concierge = cell(line(r, "1.3.10"), "2026-10");
        assertThat(concierge.actual()).isEqualByComparingTo("86816.34");
        assertThat(concierge.planned()).isEqualByComparingTo("80022.96");
        assertThat(concierge.difference()).isNull();
        assertThat(cell(line(r, "1.1.1"), "2026-10").actual()).isEqualByComparingTo("15671.02");
        // No line and no group has a difference in October: "só linhas com estouro no mês" lists nothing
        assertThat(r.groups()).allSatisfy(g -> {
            assertThat(cell(g, "2026-10").difference()).isNull();
            assertThat(g.lines()).allSatisfy(l -> assertThat(cell(l, "2026-10").difference()).isNull());
        });
        // September is complete: differences as before; cumulative and line balance only with it
        assertThat(cell(line(r, "1.3.10"), "2026-09").difference()).isEqualByComparingTo("6793.38");
        assertThat(month(r, "2026-09").overrun()).isEqualByComparingTo("38880.19");
        assertThat(line(r, "1.1.1").balance()).isEqualByComparingTo("9556.62");
        assertThat(line(r, "1.3.10").monthsAbove()).isEqualTo(1);
    }

    @Test
    void accountsWithoutConfirmedMappingAreWarnedWithTheAccountsAndTheAmount() {
        // D6: as in the budget vs. actual (NO_CONFIRMED_MAPPING), over the cumulative of the grid
        s.entries.add(s.entry(s.septemberFile, OPERATING, "9001", "0.00", "700.00", false, false,
                SEPTEMBER.atDay(10)));
        s.entries.add(s.entry(s.septemberFile, OPERATING, "9002", "0.00", "50.00", false, false,
                SEPTEMBER.atDay(11)));
        s.entries.add(s.entry(s.septemberFile, OPERATING, "9001", "0.00", "300.00", false, false,
                SEPTEMBER.atDay(12)));

        BudgetTrackingResponse r = s.grid(s.confirmedProfiles()).result();

        assertThat(r.blocks().withoutBudgetLine().cumulative()).isEqualByComparingTo("1050.00");
        assertThat(r.blocks().withoutBudgetLine().accounts())
                .extracting(a -> a.account() + "=" + a.amount().toPlainString() + "/" + a.entries())
                .containsExactly("9001=1000.00/2", "9002=50.00/1");
        assertThat(r.blocks().withoutBudgetLine().accounts().getFirst().detail()).isEqualTo("conta sem linha encontrada");
        assertThat(r.blocks().separateApportionment().accounts()).isEmpty();
        assertThat(r.warnings()).filteredOn(w -> w.code().equals("NO_CONFIRMED_MAPPING")).singleElement()
                .satisfies(w -> assertThat(w.text()).isEqualTo("2 contas do extrato sem linha encontrada na PO no acumulado:"
                        + " R$ 1.050,00 em \"sem linha da PO\", fora das linhas da PO; o previsto das linhas dessas"
                        + " despesas fica como saldo da linha. Contas: 9001 CONTA 9001 R$ 1.000,00; 9002 CONTA 9002"
                        + " R$ 50,00"));
        // The budget vs. actual of the month counts the same accounts
        var month = BudgetVsActualCalculator.calculate(BudgetTrackingGrid.withPeriod(s.base(), new Month(SEPTEMBER)))
                .result();
        assertThat(month.mapping().withoutConfirmedMapping()).isEqualTo(2);
    }

    @Test
    void withEveryAccountMappedThereIsNoMappingWarning() {
        BudgetTrackingResponse r = s.grid(s.confirmedProfiles()).result();

        assertThat(r.warnings()).noneMatch(w -> w.code().equals("NO_CONFIRMED_MAPPING"));
        assertThat(r.blocks().withoutBudgetLine().accounts()).isEmpty();
    }

    static TrackingMonthResponse month(BudgetTrackingResponse r, String month) {
        return r.months().stream().filter(m -> m.month().equals(month)).findFirst().orElseThrow();
    }

    static TrackingLineResponse line(BudgetTrackingResponse r, String code) {
        return r.groups().stream().flatMap(g -> g.lines().stream()).filter(l -> l.code().equals(code)).findFirst()
                .orElseThrow();
    }

    static TrackingCellResponse cell(TrackingLineResponse l, String month) {
        return l.cells().stream().filter(c -> c.month().equals(month)).findFirst().orElseThrow();
    }

    static TrackingCellResponse cell(TrackingGroupResponse g, String month) {
        return g.cells().stream().filter(c -> c.month().equals(month)).findFirst().orElseThrow();
    }
}
