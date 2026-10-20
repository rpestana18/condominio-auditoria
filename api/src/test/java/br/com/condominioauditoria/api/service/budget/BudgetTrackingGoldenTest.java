package br.com.condominioauditoria.api.service.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import br.com.condominioauditoria.api.dto.request.budget.SpendingProfileBatchRequest;
import br.com.condominioauditoria.api.dto.request.budget.SpendingProfileItemRequest;
import br.com.condominioauditoria.api.dto.response.accounting.FundMonthMovementResponse;
import br.com.condominioauditoria.api.dto.response.accounting.FundMovementResponse;
import br.com.condominioauditoria.api.dto.response.budget.BalanceBridgeResponse;
import br.com.condominioauditoria.api.dto.response.budget.BridgeItemResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualLineResponse;
import br.com.condominioauditoria.api.dto.response.budget.TrackingCellResponse;
import br.com.condominioauditoria.api.dto.response.budget.TrackingLineResponse;
import br.com.condominioauditoria.api.model.accounting.Fund;
import br.com.condominioauditoria.api.model.accounting.FundBalance;
import br.com.condominioauditoria.api.model.enums.BalanceBridgeItemCode;
import br.com.condominioauditoria.api.model.enums.MonthStatus;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Acceptance case of RF-13.9 with the private golden of September/2026: the grid of RF-13.4 has exactly the numbers of
 * the budget vs. actual (RF-03.1.15, unchanged), the 21 funds of RF-13.5 and the bridge of RF-13.6. Skipped without
 * data/golden/privado.
 */
class BudgetTrackingGoldenTest {

    @Test
    void septemberGridIsTheBudgetVsActualOfTheMonth() {
        SeptemberGolden g = golden();
        g.confirmMap();

        var grid = g.scenario.tracking.get(g.scenario.condominiumId, g.budget.getId());
        var month = g.september().result();

        assertThat(grid.months()).filteredOn(m -> m.status() == MonthStatus.NO_CASH_FLOW).hasSize(11);
        var sep = grid.months().stream().filter(m -> m.month().equals("2026-09")).findFirst().orElseThrow();
        assertThat(sep.actualInLines()).isEqualByComparingTo("445125.96");
        assertThat(sep.planned()).isEqualByComparingTo("451620.13");
        assertThat(sep.overrun()).isEqualByComparingTo("38880.19");
        assertThat(sep.linesAbove()).isEqualTo(25);
        Map<java.util.UUID, BudgetVsActualLineResponse> byId = month.groups().stream()
                .flatMap(gr -> gr.lines().stream())
                .collect(Collectors.toMap(BudgetVsActualLineResponse::lineId, Function.identity()));
        List<TrackingLineResponse> lines = grid.groups().stream().flatMap(gr -> gr.lines().stream()).toList();
        assertThat(lines).hasSize(byId.size());
        for (TrackingLineResponse l : lines) {
            TrackingCellResponse c = l.cells().stream().filter(x -> x.month().equals("2026-09")).findFirst()
                    .orElseThrow();
            assertThat(c.actual()).isEqualByComparingTo(byId.get(l.lineId()).actual());
            assertThat(c.difference()).isEqualByComparingTo(byId.get(l.lineId()).difference());
        }
        assertThat(lines).filteredOn(l -> l.code().equals("1.3.10")).singleElement().satisfies(l -> assertThat(
                l.balance()).isEqualByComparingTo("-6793.38"));
        assertThat(lines).filteredOn(l -> l.code().equals("1.1.1")).singleElement().satisfies(l -> assertThat(
                l.balance()).isEqualByComparingTo("9556.62"));
    }

    @Test
    void septemberFundsAndBridge() {
        SeptemberGolden g = golden();
        g.confirmMap();
        positions(g);
        // "com os tipos sugeridos confirmados" (RF-13.6)
        var profiles = g.scenario.spendingProfiles.list(g.scenario.condominiumId, g.budget.getId());
        g.scenario.spendingProfiles.confirm(g.scenario.condominiumId, g.budget.getId(),
                new SpendingProfileBatchRequest(profiles.lines().stream()
                        .map(l -> new SpendingProfileItemRequest(l.lineId(), null)).toList(), null), "admin");

        var funds = g.scenario.fundMovements.calculate(g.scenario.condominiumId, YearMonth.of(2026, 9),
                YearMonth.of(2026, 9));
        assertThat(funds.funds()).hasSize(21);
        assertThat(month(funds.funds(), "CONDOMÍNIO").closingBalance()).isEqualByComparingTo("297514.48");
        FundMonthMovementResponse reserve = month(funds.funds(), "FUNDO DE RESERVA");
        assertThat(reserve.collected()).isEqualByComparingTo("14260.79");
        assertThat(reserve.planned()).isEqualByComparingTo("13548.60");
        assertThat(funds.funds()).allSatisfy(f -> assertThat(f.months().getFirst().balanceMatches())
                .as(f.name()).isTrue());

        BalanceBridgeResponse bridge = g.scenario.bridge.get(g.scenario.condominiumId, null, "2026-09", "2026-09");
        assertThat(item(bridge, BalanceBridgeItemCode.PROVISIONS_RESERVED).amount()).isEqualByComparingTo("2177.25");
        assertThat(item(bridge, BalanceBridgeItemCode.PROVISIONS_RESERVED).lines())
                .extracting(l -> l.code() + "=" + l.amount().toPlainString())
                .containsExactlyInAnyOrder("1.1.5=1585.14", "1.1.6=-2265.84", "1.1.12=2857.95");
        assertThat(item(bridge, BalanceBridgeItemCode.LINE_SAVINGS).amount()).isEqualByComparingTo("4316.92");
        assertThat(item(bridge, BalanceBridgeItemCode.OUTSIDE_BUDGET).parts())
                .extracting(p -> p.code() + "=" + p.amount().toPlainString())
                .contains("TO_REALLOCATE=-1050.93", "ADJUSTMENTS=-3278.24");
        assertThat(item(bridge, BalanceBridgeItemCode.UNEXPLAINED).amount()).isEqualByComparingTo("0.00");
        assertThat(bridge.items().stream().map(BridgeItemResponse::amount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(bridge.variation());
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

    private static FundMonthMovementResponse month(List<FundMovementResponse> funds, String name) {
        return funds.stream().filter(f -> f.name().equals(name)).findFirst().orElseThrow().months().getFirst();
    }

    private static BridgeItemResponse item(BalanceBridgeResponse r, BalanceBridgeItemCode code) {
        return r.items().stream().filter(i -> i.code() == code).findFirst().orElseThrow();
    }

    private static SeptemberGolden golden() {
        var g = SeptemberGolden.load();
        assumeTrue(g.isPresent(), "golden privado ausente (data/golden/privado)");
        return g.get();
    }
}
