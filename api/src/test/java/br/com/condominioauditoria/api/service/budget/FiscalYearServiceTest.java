package br.com.condominioauditoria.api.service.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.condominioauditoria.api.dto.request.budget.BudgetConfirmationRequest;
import br.com.condominioauditoria.api.dto.request.budget.BudgetExtensionRequest;
import br.com.condominioauditoria.api.dto.response.budget.FiscalYearMonthSummaryResponse;
import br.com.condominioauditoria.api.dto.response.budget.FiscalYearResponse;
import br.com.condominioauditoria.api.dto.response.budget.GroupDifferenceResponse;
import br.com.condominioauditoria.api.dto.response.budget.PrintedColumnCheckResponse;
import br.com.condominioauditoria.api.dto.response.budget.PrintedColumnGroupResponse;
import br.com.condominioauditoria.api.dto.response.budget.PrintedColumnLineResponse;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.enums.FiscalYearType;
import br.com.condominioauditoria.api.model.enums.MonthStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * RF-11.4 and RF-11.5 (ADR 0005, Decision 3): fiscal year list and "PO anterior pela coluna impressa", with the real
 * "Orçado anterior" column of the pilot budget 2026/2027 (PDF values in the groups and in the cited lines).
 */
class FiscalYearServiceTest {

    private static final String WARNING_1_6 = "Grupo 1.6 DESPESAS ADMINISTRATIVAS: subtotal impresso 18.525,42; soma das"
            + " linhas 18.746,25 (diferença 220,83). Vale a soma das linhas.";
    private static final String WARNING_TOTAL = "Nesta coluna o total impresso (441.304,38) não inclui os fundos: confere"
            + " com a soma dos subtotais sem os fundos (441.304,39).";

    private final BudgetScenario scenario = new BudgetScenario();

    @Test
    void withoutPreviousBudgetPrintedColumnBecomesFiscalYear2025To2026WithPlannedOnly() {
        Budget budget = confirm(PilotBudget.defaults().withPreviousColumn(), "2026-05", "2027-04");

        List<FiscalYearResponse> list = scenario.fiscalYears.list(scenario.condominiumId);

        assertThat(list).extracting(FiscalYearResponse::id).containsExactly("po:" + budget.getId(),
                "coluna:" + budget.getId());
        FiscalYearResponse current = list.getFirst();
        assertThat(current.type()).isEqualTo(FiscalYearType.PO);
        assertThat(current.label()).isEqualTo("2026/2027");
        assertThat(current.version()).isEqualTo(1);
        assertThat(current.monthlyPlanned()).isEqualByComparingTo("451620.13");
        assertThat(current.months()).hasSize(12).allMatch(m -> m.status() == MonthStatus.SEM_FLUXO && !m.extended());
        assertThat(current.mapping()).isNotNull();
        assertThat(current.budgetItems().lines()).isPositive();
        assertThat(current.printedColumn()).isNull();

        FiscalYearResponse column = list.get(1);
        assertThat(column.type()).isEqualTo(FiscalYearType.COLUNA_IMPRESSA);
        assertThat(column.label()).isEqualTo("2025/2026 (coluna impressa)");
        assertThat(column.budgetId()).isEqualTo(budget.getId());
        assertThat(column.start()).isEqualTo("2025-05");
        assertThat(column.end()).isEqualTo("2026-04");
        assertThat(column.months()).isEmpty();
        assertThat(column.mapping()).isNull();
        assertThat(column.budgetItems()).isNull();
        // Sum of the lines, as in the confirmed budget (Q29): 1.6 comes in with 18.746,25
        assertThat(column.monthlyPlanned()).isEqualByComparingTo("441525.22");
        assertThat(column.warnings()).containsExactly(WARNING_1_6, WARNING_TOTAL);
    }

    @Test
    void columnCheckShowsGroupsFundsAndLine1320() {
        Budget budget = confirm(PilotBudget.defaults().withPreviousColumn(), "2026-05", "2027-04");

        PrintedColumnCheckResponse c = scenario.fiscalYears.printedColumn(scenario.condominiumId, budget.getId());

        assertThat(c.id()).isEqualTo("coluna:" + budget.getId());
        assertThat(c.superseded()).isFalse();
        assertThat(c.previousBudgetId()).isNull();
        assertThat(c.printedTotal()).isEqualByComparingTo("441304.38");
        assertThat(c.totalIncludesFunds()).isFalse();
        assertThat(c.funds()).isEqualByComparingTo("22065.22");
        assertThat(c.monthlyPlanned()).isEqualByComparingTo("441525.22");
        assertThat(c.groups()).extracting(PrintedColumnGroupResponse::code, g -> g.amount().toPlainString(),
                PrintedColumnGroupResponse::matches)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("1.1", "37661.43", true),
                        org.assertj.core.groups.Tuple.tuple("1.2", "565.00", true),
                        org.assertj.core.groups.Tuple.tuple("1.3", "348631.55", true),
                        org.assertj.core.groups.Tuple.tuple("1.4", "0.00", true),
                        org.assertj.core.groups.Tuple.tuple("1.5", "2350.00", true),
                        org.assertj.core.groups.Tuple.tuple("1.6", "18746.25", false),
                        org.assertj.core.groups.Tuple.tuple("1.7", "19300.00", true),
                        org.assertj.core.groups.Tuple.tuple("1.8", "14270.99", true),
                        org.assertj.core.groups.Tuple.tuple("1.9", "22065.22", true));
        PrintedColumnGroupResponse administrative = c.groups().get(5);
        assertThat(administrative.printed()).isEqualByComparingTo("18525.42");
        assertThat(administrative.difference()).isEqualByComparingTo("-220.83");
        assertThat(c.groups().get(8).funds()).isTrue();
        PrintedColumnLineResponse managementFee = c.groups().get(2).lines().stream().filter(l -> l.code().equals("1.3.20"))
                .findFirst().orElseThrow();
        assertThat(managementFee.amount()).isEqualByComparingTo("17195.00");
        assertThat(managementFee.percentageText()).isEqualTo("-53,47%");
        assertThat(scenario.line(budget, "1.3.20", 0).getBudgeted()).isEqualByComparingTo("8000.00");
        assertThat(c.differences()).isEmpty();
        assertThat(c.warnings()).containsExactly(WARNING_1_6, WARNING_TOTAL);
    }

    @Test
    void previousBudgetConfirmedLaterReplacesColumnWithWarningPerGroup() {
        Budget current = confirm(PilotBudget.defaults().withPreviousColumn(), "2026-05", "2027-04");
        // Test copy of the pilot budget confirmed as 2025/2026: Personnel 69.193,86 by the sum of the lines
        Budget previous = confirm(PilotBudget.defaults(), "2025-05", "2026-04");

        List<FiscalYearResponse> list = scenario.fiscalYears.list(scenario.condominiumId);

        assertThat(list).extracting(FiscalYearResponse::id).containsExactly("po:" + current.getId(),
                "po:" + previous.getId());
        FiscalYearResponse previousFiscalYear = list.get(1);
        assertThat(previousFiscalYear.label()).isEqualTo("2025/2026");
        assertThat(previousFiscalYear.printedColumn()).isEqualTo("coluna:" + current.getId());
        assertThat(previousFiscalYear.warnings()).contains("A coluna \"Orçado anterior\" difere da PO anterior enviada no grupo"
                + " 1.1 PESSOAL: 69.193,86 (PO enviada) × 37.661,43 (coluna impressa)");
        PrintedColumnCheckResponse c = scenario.fiscalYears.printedColumn(scenario.condominiumId, current.getId());
        assertThat(c.superseded()).isTrue();
        assertThat(c.previousBudgetId()).isEqualTo(previous.getId());
        assertThat(c.previousBudgetLabel()).isEqualTo("2025/2026");
        assertThat(c.differences()).extracting(GroupDifferenceResponse::code).contains("1.1");
        assertThat(c.warnings()).contains(WARNING_1_6);
    }

    @Test
    void monthsShowCashFlowAndExtension() {
        Budget budget = confirm(PilotBudget.defaults(), "2026-05", "2027-04");
        scenario.cashFlow("fluxo-2026-09.pdf", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 0);
        scenario.extension.extend(scenario.condominiumId, budget.getId(),
                new BudgetExtensionRequest("2027-05", "PO 2027/2028 ainda não aprovada"), "admin");

        FiscalYearResponse ex = scenario.fiscalYears.list(scenario.condominiumId).getFirst();

        assertThat(ex.months()).hasSize(13);
        assertThat(ex.months().get(4)).isEqualTo(new FiscalYearMonthSummaryResponse("2026-09", MonthStatus.COM_FLUXO,
                false));
        assertThat(ex.months().getLast()).isEqualTo(new FiscalYearMonthSummaryResponse("2027-05",
                MonthStatus.SEM_FLUXO, true));
        assertThat(ex.extension().until()).isEqualTo("2027-05");
    }

    @Test
    void budgetWithoutColumnHasNoVirtualFiscalYear() {
        Budget budget = confirm(PilotBudget.defaults(), "2026-05", "2027-04");

        assertThat(scenario.fiscalYears.list(scenario.condominiumId)).extracting(FiscalYearResponse::id)
                .containsExactly("po:" + budget.getId());
        assertThatThrownBy(() -> scenario.fiscalYears.printedColumn(scenario.condominiumId, budget.getId()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void unconfirmedBudgetIsNotListed() {
        scenario.readBudget(PilotBudget.defaults().withPreviousColumn());

        assertThat(scenario.fiscalYears.list(scenario.condominiumId)).isEmpty();
    }

    @Test
    void columnMatchesTotalIncludingFundsWithoutWarning() {
        Budget budget = confirm(PilotBudget.defaults().withPreviousColumn().withPreviousTotal("463369.60"),
                "2026-05", "2027-04");

        PrintedColumnCheckResponse c = scenario.fiscalYears.printedColumn(scenario.condominiumId, budget.getId());

        assertThat(c.totalIncludesFunds()).isTrue();
        assertThat(c.warnings()).containsExactly(WARNING_1_6);
        assertThat(c.monthlyPlanned()).isEqualByComparingTo(new BigDecimal("441525.22"));
    }

    private Budget confirm(PilotBudget pilot, String start, String end) {
        Budget budget = scenario.readBudget(pilot);
        var p = scenario.pilotRequest(budget);
        scenario.confirmation.confirm(scenario.condominiumId, budget.getId(), new BudgetConfirmationRequest(start, end,
                p.minutesFileId(), false, LocalDate.of(2026, 5, 20), p.effectiveCodes(), p.funds(), false, false,
                null), "admin");
        return budget;
    }
}
