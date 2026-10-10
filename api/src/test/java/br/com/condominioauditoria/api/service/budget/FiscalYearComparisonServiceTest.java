package br.com.condominioauditoria.api.service.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.condominioauditoria.api.dto.request.budget.BudgetConfirmationRequest;
import br.com.condominioauditoria.api.dto.response.budget.ComparedGroupResponse;
import br.com.condominioauditoria.api.dto.response.budget.ComparedItemResponse;
import br.com.condominioauditoria.api.dto.response.budget.ComparedValueResponse;
import br.com.condominioauditoria.api.dto.response.budget.FiscalYearComparisonResponse;
import br.com.condominioauditoria.api.dto.response.budget.FiscalYearSummaryResponse;
import br.com.condominioauditoria.api.dto.response.budget.VariationResponse;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.Enrichment;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.LedgerEntryData;
import br.com.condominioauditoria.api.model.accounting.LedgerEntry;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.enums.FiscalYearType;
import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.service.calculator.FiscalYearComparison;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * RF-11.6 (ADR 0005, Decision 2): fiscal year comparison. Pilot budget 2026/2027 with the real "Orçado anterior"
 * column and, for two fiscal years with a budget, the test copy of the pilot budget confirmed as 05/2025 to 04/2026.
 */
class FiscalYearComparisonServiceTest {

    private final BudgetScenario scenario = new BudgetScenario();

    @Test
    void withoutPreviousBudgetComparesWithPrintedColumn() {
        Budget budget = confirm(PilotBudget.defaults().withPreviousColumn(), "2026-05", "2027-04");

        FiscalYearComparisonResponse r = scenario.comparison.compare(scenario.condominiumId, null, null, false);

        assertThat(r.fiscalYears()).extracting(e -> e.id(), e -> e.type(), e -> e.label()).containsExactly(
                org.assertj.core.groups.Tuple.tuple("budget:" + budget.getId(), FiscalYearType.PO, "2026/2027"),
                org.assertj.core.groups.Tuple.tuple("column:" + budget.getId(), FiscalYearType.PRINTED_COLUMN,
                        "2025/2026 (coluna impressa)"));
        FiscalYearSummaryResponse current = r.summary().getFirst();
        FiscalYearSummaryResponse column = r.summary().get(1);
        assertThat(current.monthlyPlanned()).isEqualByComparingTo("451620.13");
        assertThat(current.fiscalYearPlanned()).isEqualByComparingTo("5419441.56");
        assertThat(column.monthlyPlanned()).isEqualByComparingTo("441525.22");
        // Month planned by the sum of the lines on both sides (Q29): +10.094,91 and +2,3%
        assertThat(current.monthlyPlannedVariation()).isEqualTo(new VariationResponse(new BigDecimal("10094.91"),
                new BigDecimal("2.3"), false));
        assertThat(column.monthlyPlannedVariation()).isNull();
        // No loaded cash flow: planned only, no actual R$ 0,00
        assertThat(current.actual()).isNull();
        assertThat(current.monthsWithCashFlow()).isZero();
        assertThat(column.actual()).isNull();
        assertThat(column.openFindings()).isNull();

        ComparedGroupResponse contracts = group(r, "1.3");
        assertThat(contracts.values().getFirst().monthlyPlanned()).isEqualByComparingTo("336274.18");
        assertThat(contracts.values().get(1).monthlyPlanned()).isEqualByComparingTo("348631.55");
        assertThat(contracts.values().getFirst().monthlyPlannedVariation()).isEqualTo(
                new VariationResponse(new BigDecimal("-12357.37"), new BigDecimal("-3.5"), false));
        assertThat(group(r, "1.9").funds()).isTrue();
        assertThat(group(r, "1.9").values().get(1).monthlyPlanned()).isEqualByComparingTo("22065.22");
    }

    @Test
    void lineByItemWithNewInFiscalYear() {
        confirm(PilotBudget.defaults().withPreviousColumn(), "2026-05", "2027-04");

        FiscalYearComparisonResponse r = scenario.comparison.compare(scenario.condominiumId, null, null, false);

        ComparedValueResponse managementFee = itemOfLine(r, "1.3.20").values().getFirst();
        assertThat(managementFee.monthlyPlanned()).isEqualByComparingTo("8000.00");
        assertThat(itemOfLine(r, "1.3.20").values().get(1).monthlyPlanned()).isEqualByComparingTo("17195.00");
        assertThat(managementFee.monthlyPlannedVariation()).isEqualTo(new VariationResponse(new BigDecimal("-9195.00"),
                new BigDecimal("-53.5"), false));
        ComparedValueResponse waterTank = itemOfLine(r, "1.3.25").values().getFirst();
        assertThat(waterTank.monthlyPlanned()).isEqualByComparingTo("1518.93");
        assertThat(waterTank.monthlyPlannedVariation()).isEqualTo(new VariationResponse(new BigDecimal("1518.93"),
                null, true));
        // The first confirmed budget gave a budget item to every line: nothing is left unmatched
        assertThat(r.unmatched()).isEmpty();
    }

    @Test
    void lineWithoutConfirmedItemIsUnmatchedAndNotSummed() {
        Budget budget = confirm(PilotBudget.defaults().withPreviousColumn(), "2026-05", "2027-04");
        BudgetLine managementFee = scenario.line(budget, "1.3.20", 0);
        scenario.lineItems.removeIf(l -> l.getBudgetLineId().equals(managementFee.getId()));

        FiscalYearComparisonResponse r = scenario.comparison.compare(scenario.condominiumId, null, null, false);

        assertThat(r.lines()).noneMatch(x -> x.values().stream().flatMap(v -> v.lines().stream())
                .anyMatch(l -> l.code().equals("1.3.20")));
        assertThat(r.unmatched()).extracting(l -> l.fiscalYearId(), l -> l.code(),
                l -> l.monthlyPlanned().toPlainString()).containsExactly(
                org.assertj.core.groups.Tuple.tuple("budget:" + budget.getId(), "1.3.20", "8000.00"),
                org.assertj.core.groups.Tuple.tuple("column:" + budget.getId(), "1.3.20", "17195.00"));
        assertThat(r.warnings()).contains("2026/2027: 1 linha sem rubrica confirmada (bloco \"sem correspondência\")");
    }

    @Test
    void sameMonthsSumsOnlySeptemberInBothFiscalYears() {
        Budget previous = confirm(PilotBudget.defaults(), "2025-05", "2026-04");
        Budget current = confirm(PilotBudget.defaults(), "2026-05", "2027-04");
        debit(scenario.cashFlow("fluxo-2025-09.pdf", LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 30), 1), "1000.00",
                LocalDate.of(2025, 9, 10));
        debit(scenario.cashFlow("fluxo-2026-09.pdf", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 1), "1500.00",
                LocalDate.of(2026, 9, 10));
        debit(scenario.cashFlow("fluxo-2026-10.pdf", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 1),
                "700.00",
                LocalDate.of(2026, 10, 10));
        List<String> ids = List.of("budget:" + previous.getId(), "budget:" + current.getId());

        FiscalYearComparisonResponse all = scenario.comparison.compare(scenario.condominiumId, ids, null, false);
        FiscalYearComparisonResponse same = scenario.comparison.compare(scenario.condominiumId, ids, null, true);

        assertThat(all.fiscalYears()).extracting(e -> e.id()).containsExactly("budget:" + current.getId(),
                "budget:" + previous.getId());
        assertThat(all.comparing()).isNull();
        assertThat(all.summary().getFirst().monthsWithCashFlow()).isEqualTo(2);
        assertThat(all.summary().getFirst().actual()).isEqualByComparingTo("2200.00");
        assertThat(all.summary().getFirst().planned()).isEqualByComparingTo("903240.26");

        assertThat(same.comparing()).isEqualTo("comparando: setembro");
        assertThat(same.fiscalYears().getFirst().months()).containsExactly("2026-09");
        assertThat(same.fiscalYears().getFirst().period()).isEqualTo("2026-09");
        FiscalYearSummaryResponse currentSeptember = same.summary().getFirst();
        FiscalYearSummaryResponse previousSeptember = same.summary().get(1);
        assertThat(currentSeptember.monthsWithCashFlow()).isEqualTo(1);
        assertThat(currentSeptember.actual()).isEqualByComparingTo("1500.00");
        assertThat(currentSeptember.planned()).isEqualByComparingTo("451620.13");
        assertThat(previousSeptember.actual()).isEqualByComparingTo("1000.00");
        assertThat(currentSeptember.actualVariation()).isEqualTo(new VariationResponse(new BigDecimal("500.00"),
                new BigDecimal("50.0"), false));
        assertThat(currentSeptember.openFindings()).isNotNull();
    }

    @Test
    void reserveFundFilterShowsOnlyItsLine() {
        Budget budget = confirm(PilotBudget.defaults().withPreviousColumn(), "2026-05", "2027-04");

        FiscalYearComparisonResponse r = scenario.comparison.compare(scenario.condominiumId, null,
                scenario.reserveFund.getId(), false);

        assertThat(r.groups()).extracting(ComparedGroupResponse::code).containsExactly("1.9");
        assertThat(r.groups().getFirst().values()).allMatch(v -> v.lines().size() == 1);
        assertThat(r.summary().getFirst().monthlyPlanned()).isEqualByComparingTo("13548.60");
        assertThat(r.summary().get(1).monthlyPlanned()).isEqualByComparingTo("13239.13");

        FiscalYearComparisonResponse condominium = scenario.comparison.compare(scenario.condominiumId, null,
                scenario.operatingFund.getId(),
                false);
        assertThat(condominium.groups()).extracting(ComparedGroupResponse::code).doesNotContain("1.9");
        assertThat(condominium.summary().getFirst().monthlyPlanned()).isEqualByComparingTo("451620.13");
        assertThat(budget).isNotNull();
    }

    @Test
    void invalidRequestIsRejected() {
        Budget budget = confirm(PilotBudget.defaults(), "2026-05", "2027-04");

        assertStatus(() -> scenario.comparison.compare(scenario.condominiumId, null, null, false),
                HttpStatus.UNPROCESSABLE_CONTENT);
        assertStatus(() -> scenario.comparison.compare(scenario.condominiumId, List.of("budget:abc",
                "budget:" + budget.getId()),
                null, false), HttpStatus.BAD_REQUEST);
        assertStatus(() -> scenario.comparison.compare(scenario.condominiumId,
                List.of("column:" + budget.getId(), "budget:" + budget.getId()), null, false), HttpStatus.NOT_FOUND);
        assertStatus(() -> scenario.comparison.compare(scenario.condominiumId,
                List.of("budget:" + java.util.UUID.randomUUID(), "budget:" + budget.getId()), null, false),
                        HttpStatus.NOT_FOUND);
    }

    @Test
    void variationAndComparedMonths() {
        assertThat(FiscalYearComparison.variation(new BigDecimal("451620.13"), new BigDecimal("441525.22")))
                .isEqualTo(new VariationResponse(new BigDecimal("10094.91"), new BigDecimal("2.3"), false));
        assertThat(FiscalYearComparison.variation(new BigDecimal("0.00"), new BigDecimal("0.00")))
                .isEqualTo(new VariationResponse(new BigDecimal("0.00"), null, false));
        assertThat(FiscalYearComparison.variation(null, BigDecimal.ONE)).isNull();
        assertThat(FiscalYearComparison.comparing(Set.of(Month.SEPTEMBER, Month.AUGUST)))
                .isEqualTo("comparando: agosto e setembro");
        assertThat(FiscalYearComparison.comparing(Set.of())).isEqualTo(
                "comparando: nenhum mês com fluxo em todos os exercícios");
    }

    private static ComparedGroupResponse group(FiscalYearComparisonResponse r, String code) {
        return r.groups().stream().filter(g -> g.code().equals(code)).findFirst().orElseThrow();
    }

    private static ComparedItemResponse itemOfLine(FiscalYearComparisonResponse r, String code) {
        return r.lines().stream().filter(x -> x.values().getFirst().lines().stream()
                .anyMatch(l -> l.code().equals(code))).findFirst().orElseThrow();
    }

    private static void assertStatus(org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
            HttpStatus status) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(status));
    }

    private Budget confirm(PilotBudget pilot, String start, String end) {
        Budget budget = scenario.readBudget(pilot);
        var p = scenario.pilotRequest(budget);
        scenario.confirmation.confirm(scenario.condominiumId, budget.getId(), new BudgetConfirmationRequest(start, end,
                p.minutesFileId(), false, LocalDate.of(2026, 5, 20), p.effectiveCodes(), p.funds(), false, false,
                null), "admin");
        return budget;
    }

    private void debit(SourceFile cashFlow, String amount, LocalDate date) {
        var read = new LedgerEntryData(1, scenario.ledgerEntries.size() + 1, date, "9999", "Teste", "", "Teste",
                BigDecimal.ZERO.setScale(2), new BigDecimal(amount), BigDecimal.ZERO.setScale(2),
                new Enrichment(null, null, null, false, false));
        scenario.ledgerEntries.add(new LedgerEntry(scenario.condominiumId, cashFlow.getId(),
                scenario.operatingFund.getId(), read));
    }
}
