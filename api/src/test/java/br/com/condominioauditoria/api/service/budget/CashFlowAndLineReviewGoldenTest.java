package br.com.condominioauditoria.api.service.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import br.com.condominioauditoria.api.dto.request.budget.FiscalYearEndRequest;
import br.com.condominioauditoria.api.dto.request.budget.LineReviewBatchRequest;
import br.com.condominioauditoria.api.dto.request.budget.LineReviewItemRequest;
import br.com.condominioauditoria.api.dto.response.accounting.CashFlowMonthResponse;
import br.com.condominioauditoria.api.dto.response.accounting.CashFlowMonthlyResponse;
import br.com.condominioauditoria.api.dto.response.accounting.CashFlowRowResponse;
import br.com.condominioauditoria.api.dto.response.budget.BalanceBridgeResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetLineWarningResponse;
import br.com.condominioauditoria.api.dto.response.budget.LineReviewLineResponse;
import br.com.condominioauditoria.api.dto.response.budget.TrackingMonthResponse;
import br.com.condominioauditoria.api.messaging.GoldenMessages;
import br.com.condominioauditoria.api.model.accounting.FundBalance;
import br.com.condominioauditoria.api.model.accounting.Fund;
import br.com.condominioauditoria.api.model.budget.BudgetEvent;
import br.com.condominioauditoria.api.model.enums.BalanceBridgeItemCode;
import br.com.condominioauditoria.api.model.enums.BudgetLineReviewStatus;
import br.com.condominioauditoria.api.model.enums.BudgetLineWarningCode;
import br.com.condominioauditoria.api.model.enums.CashFlowRowCode;
import br.com.condominioauditoria.api.model.enums.FileCategory;
import br.com.condominioauditoria.api.model.enums.OriginMethod;
import br.com.condominioauditoria.api.model.enums.OriginSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/**
 * RF-13.11, RF-13.12, RF-13.13 and RF-13.17 (ADR 0009) with the pilot's September/2026 cash flow and 2026/2027 budget
 * (private golden, outside git). Numbers of the revised RF-13.
 */
class CashFlowAndLineReviewGoldenTest {

    @Test
    void reviewShowsThe88LinesWithTheWarningsOfThePilot() {
        SeptemberGolden g = golden();

        var review = g.scenario.lineReview.list(g.scenario.condominiumId, g.budget.getId());

        assertThat(review.lines()).hasSize(88);
        assertThat(review.lines()).filteredOn(LineReviewLineResponse::funds).hasSize(2);
        // Q54: the lines read are valid by themselves; the warnings are only signals
        assertThat(review.state().reviewed()).isTrue();
        assertThat(review.state().totalLines()).isEqualTo(88);
        assertThat(review.lines()).allSatisfy(l -> assertThat(l.status()).isEqualTo(BudgetLineReviewStatus.PENDING));
        assertThat(codes(review.lines(), BudgetLineWarningCode.ACCOUNT_DESCRIPTION_MISMATCH)).contains("1.1.2");
        assertThat(codes(review.lines(), BudgetLineWarningCode.GROUP_ACCOUNT_SUGGESTED)).contains("1.1.1");
        List<String> twoLines = review.lines().stream().flatMap(l -> l.warnings().stream())
                .filter(w -> w.code() == BudgetLineWarningCode.ACCOUNT_IN_TWO_LINES)
                .map(w -> w.message().substring("Conta ".length(), "Conta ".length() + 4)).distinct().toList();
        assertThat(twoLines).containsExactlyInAnyOrder("1600", "1606", "1693", "1624");
        assertThat(codes(review.lines(), BudgetLineWarningCode.REPEATED_ITEM)).containsOnly("1.3.2", "1.3.25");
        assertThat(codes(review.lines(), BudgetLineWarningCode.ZERO_BUDGETED)).contains("1.4.1", "1.1.10");
        // Without a support sheet: "origem não informada", never a block
        assertThat(review.lines()).allSatisfy(l -> assertThat(l.origin()).isNull());
    }

    @Test
    void withoutAnyLineReviewTheScreensAlreadyShowTheNumbersByLine() {
        // Q54 (29/10/2026): no pending step; the review warnings block no screen
        SeptemberGolden g = golden();
        g.confirmMap();
        positions(g);

        var grid = g.scenario.tracking.get(g.scenario.condominiumId, g.budget.getId());
        assertThat(grid.groups()).isNotEmpty();
        assertThat(grid.lineReview().reviewed()).isTrue();
        assertThat(grid.warnings()).noneMatch(w -> w.code().equals("LINES_NOT_REVIEWED"));
        BalanceBridgeResponse bridge = g.scenario.bridge.get(g.scenario.condominiumId, null, "2026-09", "2026-09");
        assertThat(bridge.items()).filteredOn(i -> i.code() == BalanceBridgeItemCode.LINE_SAVINGS).singleElement()
                .satisfies(i -> assertThat(i.amount()).isNotNull());
        assertThat(bridge.variation()).isEqualByComparingTo("43712.45");

        CashFlowMonthlyResponse r = g.scenario.cashFlow.monthly(g.scenario.condominiumId, null, null, null);
        CashFlowMonthResponse sep = month(r, "2026-09");
        assertThat(sep.byAccount()).isFalse();
        assertThat(sep.outflows()).isEqualByComparingTo("449455.13");
        assertThat(sep.inLines()).isEqualByComparingTo("445125.96");
        assertThat(r.warnings()).noneMatch(w -> w.code().equals("LINES_NOT_REVIEWED"));
    }

    @Test
    void septemberInflowsAndOutflowsByLineOfTheReviewedBudget() {
        SeptemberGolden g = golden();
        g.confirmMap();
        positions(g);
        g.scenario.reviewAllLines(g.budget);

        CashFlowMonthlyResponse r = g.scenario.cashFlow.monthly(g.scenario.condominiumId, null, null, null);

        assertThat(r.fundName()).isEqualTo("CONDOMÍNIO");
        assertThat(r.from()).isEqualTo("2026-09");
        assertThat(r.to()).isEqualTo("2026-09");
        CashFlowMonthResponse sep = month(r, "2026-09");
        assertThat(sep.beforeBudget()).isFalse();
        assertThat(sep.byAccount()).isFalse();
        assertThat(sep.outflows()).isEqualByComparingTo("449455.13");
        assertThat(sep.inLines()).isEqualByComparingTo("445125.96");
        assertThat(block(r, CashFlowRowCode.ADJUSTMENTS).total()).isEqualByComparingTo("3278.24");
        assertThat(block(r, CashFlowRowCode.TO_REALLOCATE).total()).isEqualByComparingTo("1050.93");
        assertThat(block(r, CashFlowRowCode.WITHOUT_BUDGET_LINE).total()).isEqualByComparingTo("0.00");
        assertThat(sep.monthBalance()).isEqualByComparingTo("43712.45");
        // RF-13.11 counts 493.167,58 as "entradas" with the transfers received: the table shows the 281,10 of the five
        // "TRANSFERENCIA CONTABIL ... PARA CONDOMINIO" apart (block 3), as RF-13.11 item 3 and the D3 fix ask
        assertThat(sep.inflows()).isEqualByComparingTo("492886.48");
        assertThat(sep.transfersIn()).isEqualByComparingTo("281.10");
        assertThat(sep.transfersOut()).isEqualByComparingTo("0.00");
        assertThat(sep.inflows().add(sep.transfersIn())).isEqualByComparingTo("493167.58")
                .isEqualByComparingTo(sep.outflows().add(sep.monthBalance()));
        assertThat(sep.closingBalance()).isEqualByComparingTo("297514.48");
        // D6: outflows + transfers = debits of the cash flow, to the cent
        assertThat(sep.debitsMatch()).isTrue();
        assertThat(sep.balanceMatches()).isTrue();
        assertThat(r.warnings()).noneMatch(w -> w.code().equals("DEBITS_DO_NOT_MATCH"));
        // Every line of the budget appears, also the zero ones; the separate apportionment lines in their block
        long lines = r.outflowGroups().stream().mapToLong(gr -> gr.lines().size()).sum();
        assertThat(lines + block(r, CashFlowRowCode.SEPARATE_APPORTIONMENT).children().size()).isLessThanOrEqualTo(86);
        assertThat(lines).isGreaterThanOrEqualTo(80);

        // The 1.3.10 cell and its entries add up (RF-13.11)
        var vigia = r.outflowGroups().stream().flatMap(gr -> gr.lines().stream())
                .filter(l -> "1.3.10".equals(l.lineCode())).findFirst().orElseThrow();
        var entries = g.scenario.cashFlow.entries(g.scenario.condominiumId, null, "2026-09", vigia.lineId(), null,
                null, null, null);
        assertThat(entries.total()).isEqualByComparingTo(vigia.total());
        assertThat(entries.entries()).isNotEmpty().allSatisfy(e -> {
            assertThat(e.destination()).isEqualTo(CashFlowRowCode.BUDGET_LINE);
            assertThat(e.lineCode()).isEqualTo("1.3.10");
            assertThat(e.fileName()).isEqualTo("fluxo-caixa-2026-09.pdf");
            assertThat(e.page()).isPositive();
        });
        // Every outflow of the month in the list, with its destination: the same total as the table
        var out = g.scenario.cashFlow.entries(g.scenario.condominiumId, null, "2026-09", null, null, null, null, null);
        BigDecimal debits = out.entries().stream().filter(e -> e.direction().equals("OUT")
                && e.destination() != CashFlowRowCode.TRANSFERS_OUT).map(e -> e.amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(debits).isEqualByComparingTo("449455.13");
    }

    @Test
    void allFundsViewHasTheOtherFundsApart() {
        SeptemberGolden g = golden();
        g.confirmMap();
        positions(g);
        g.scenario.reviewAllLines(g.budget);

        CashFlowMonthlyResponse r = g.scenario.cashFlow.monthly(g.scenario.condominiumId, "all", null, null);

        assertThat(r.allFunds()).isTrue();
        CashFlowRowResponse others = block(r, CashFlowRowCode.OTHER_FUNDS);
        assertThat(others.children()).hasSize(20);
        CashFlowMonthResponse sep = month(r, "2026-09");
        assertThat(sep.debitsMatch()).isTrue();
        assertThat(sep.monthBalance()).isEqualByComparingTo(sep.closingBalance().subtract(sep.openingBalance()));
    }

    @Test
    void adminReviewsMarksAndTheTrailRecordsWhoAndWhen() {
        SeptemberGolden g = golden();
        var lines = g.scenario.lineReview.list(g.scenario.condominiumId, g.budget.getId()).lines();
        var staff = lines.stream().filter(l -> "1.1.1".equals(l.code())).findFirst().orElseThrow();

        var r = g.scenario.lineReview.review(g.scenario.condominiumId, g.budget.getId(), new LineReviewBatchRequest(
                List.of(new LineReviewItemRequest(staff.lineId(), BudgetLineReviewStatus.REVIEWED, true, null)), false,
                "conferido com o PDF"), "admin");

        var after = r.lines().stream().filter(l -> l.lineId().equals(staff.lineId())).findFirst().orElseThrow();
        assertThat(after.status()).isEqualTo(BudgetLineReviewStatus.REVIEWED);
        assertThat(after.reviewedBy()).isEqualTo("admin");
        assertThat(after.reviewedAt()).isNotNull();
        assertThat(after.groupAccount()).isTrue();
        assertThat(after.warnings()).extracting(BudgetLineWarningResponse::code)
                .contains(BudgetLineWarningCode.GROUP_ACCOUNT).doesNotContain(BudgetLineWarningCode.GROUP_ACCOUNT_SUGGESTED);
        assertThat(r.state().reviewedLines()).isEqualTo(1);
        assertThat(g.scenario.lineReview.events(g.scenario.condominiumId, g.budget.getId()))
                .extracting(e -> e.action().name() + ":" + e.lineCode() + ":" + e.username())
                .containsExactly("MARKS_CHANGED:1.1.1:admin", "REVIEWED:1.1.1:admin");

        // "Conferir todas": the rest; nothing changes in the line already reviewed
        var all = g.scenario.lineReview.review(g.scenario.condominiumId, g.budget.getId(),
                new LineReviewBatchRequest(List.of(), true, null), "admin");
        assertThat(all.state().reviewed()).isTrue();
        assertThat(all.state().reviewedLines()).isEqualTo(88);
        assertThat(g.scenario.lineReviewEvents).hasSize(2 + 87);
    }

    @Test
    void supportSheetGivesTheOriginOfEachLine() {
        SeptemberGolden g = golden();
        var sheet = GoldenMessages.read("po-2026-2027-planilha-apoio");
        assumeTrue(sheet.isPresent(), "planilha de apoio ausente no golden privado");
        var file = g.scenario.file(FileCategory.PO, "PO-2026-2027-planilha-apoio.xlsx");

        var saved = g.scenario.lineOriginService.saveFromSheet(file, sheet.get().budgetLineOrigins());

        assertThat(saved.budget().getId()).isEqualTo(g.budget.getId());
        assertThat(saved.saved()).isEqualTo(86);
        var review = g.scenario.lineReview.list(g.scenario.condominiumId, g.budget.getId());
        assertThat(review.linesWithOrigin()).isEqualTo(86);
        var vigia = review.lines().stream().filter(l -> "1.3.10".equals(l.code())).findFirst().orElseThrow();
        assertThat(vigia.origin().sourceAccounts()).containsExactly("1692");
        assertThat(vigia.origin().averageFrom()).isEqualTo("2026-01");
        assertThat(vigia.origin().averageTo()).isEqualTo("2026-04");
        assertThat(vigia.origin().averageAmount()).isEqualByComparingTo("74627.40");
        assertThat(vigia.origin().method()).isEqualTo(OriginMethod.AVERAGE_TIMES_INDEX);
        assertThat(vigia.origin().indexName()).isEqualTo("VIGILANTES RJ");
        assertThat(vigia.origin().indexRate()).isEqualByComparingTo("0.0723");
        assertThat(vigia.origin().finalAmount()).isEqualByComparingTo("80022.96");
        assertThat(vigia.budgeted()).isEqualByComparingTo("80022.96");
        assertThat(vigia.origin().source()).isEqualTo(OriginSource.SHEET);
        assertThat(vigia.origin().fileName()).isEqualTo("PO-2026-2027-planilha-apoio.xlsx");
        assertThat(vigia.origin().sheet()).isEqualTo("PO-26-27");
        assertThat(vigia.origin().sheetRow()).isEqualTo(30);
        var garden = review.lines().stream().filter(l -> "1.3.8".equals(l.code())).findFirst().orElseThrow();
        assertThat(garden.origin().averageAmount()).isEqualByComparingTo("13480.00");
        assertThat(garden.origin().method()).isEqualTo(OriginMethod.TYPED);
        assertThat(garden.origin().finalAmount()).isEqualByComparingTo("18000.00");
        assertThat(garden.origin().reason()).isEqualTo("Negociação contratual");
        assertThat(garden.budgeted()).isEqualByComparingTo("18000.00");
        // The values of the sheet and of the PDF match in the 86 lines (O4)
        assertThat(codes(review.lines(), BudgetLineWarningCode.ORIGIN_FINAL_DIFFERS)).isEmpty();
    }

    @Test
    void fiscalYearEndCorrectedToFebruaryGivesTenColumns() {
        SeptemberGolden g = golden();
        g.confirmMap();
        g.scenario.reviewAllLines(g.budget);
        var detail = g.scenario.query.detail(g.budget);
        assertThat(detail.suggestedFiscalYearEnd()).isEqualTo("2027-02");

        g.scenario.extension.correctFiscalYearEnd(g.scenario.condominiumId, g.budget.getId(),
                new FiscalYearEndRequest("2027-02", "exercício de março a março (D2)"), "admin");

        assertThat(g.budget.getFiscalYearEnd()).hasToString("2027-02");
        assertThat(g.scenario.budgetEvents).filteredOn(e -> e.getType().equals(BudgetEvent.FISCAL_YEAR_END_CORRECTED))
                .singleElement().satisfies(e -> {
                    assertThat(e.getDetail()).isEqualTo("Fim do exercício corrigido de 2027-04 para 2027-02.");
                    assertThat(e.getUsername()).isEqualTo("admin");
                    assertThat(e.getJustification()).isEqualTo("exercício de março a março (D2)");
                });
        var grid = g.scenario.tracking.get(g.scenario.condominiumId, g.budget.getId());
        assertThat(grid.months()).hasSize(10);
        assertThat(grid.months().getFirst().month()).isEqualTo("2026-05");
        assertThat(grid.months().getLast().month()).isEqualTo("2027-02");
        // The planned of the fiscal year: 10 columns × 451.620,13 (only the months with a cash flow show it)
        BigDecimal monthly = grid.months().stream().filter(m -> m.month().equals("2026-09"))
                .map(TrackingMonthResponse::planned).filter(Objects::nonNull).findFirst().orElseThrow();
        assertThat(monthly).isEqualByComparingTo("451620.13");
        assertThat(monthly.multiply(BigDecimal.valueOf(grid.months().size()))).isEqualByComparingTo("4516201.30");
    }

    private static List<String> codes(List<LineReviewLineResponse> lines, BudgetLineWarningCode code) {
        return lines.stream().filter(l -> l.warnings().stream().anyMatch(w -> w.code() == code))
                .map(LineReviewLineResponse::code).toList();
    }

    private static CashFlowMonthResponse month(CashFlowMonthlyResponse r, String month) {
        return r.months().stream().filter(m -> m.month().equals(month)).findFirst().orElseThrow();
    }

    private static CashFlowRowResponse block(CashFlowMonthlyResponse r, CashFlowRowCode code) {
        return r.outflowBlocks().stream().filter(b -> b.code() == code).findFirst().orElseThrow();
    }

    /** The printed positions of the September cash flow, as the ProcessingResultService stores them. */
    private static void positions(SeptemberGolden g) {
        var read = g.cashFlow.cashFlow();
        for (var p : read.financialPosition()) {
            Fund f = g.funds.computeIfAbsent(p.fund(), n -> {
                Fund created = new Fund(g.scenario.condominiumId, n);
                g.scenario.funds.add(created);
                return created;
            });
            g.scenario.fundBalances.add(new FundBalance(g.scenario.condominiumId, g.cashFlowFileId, f.getId(),
                    read.periodStart(), read.periodEnd(), p));
        }
    }

    private static SeptemberGolden golden() {
        var g = SeptemberGolden.load();
        assumeTrue(g.isPresent(), "golden privado ausente (data/golden/privado)");
        return g.get();
    }
}
