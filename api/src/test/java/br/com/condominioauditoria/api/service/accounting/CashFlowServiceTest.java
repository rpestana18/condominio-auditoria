package br.com.condominioauditoria.api.service.accounting;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.api.dto.request.budget.AccountMappingBatchRequest;
import br.com.condominioauditoria.api.dto.response.accounting.CashFlowMonthResponse;
import br.com.condominioauditoria.api.dto.response.accounting.CashFlowMonthlyResponse;
import br.com.condominioauditoria.api.dto.response.accounting.CashFlowRowResponse;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.Enrichment;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.LedgerEntryData;
import br.com.condominioauditoria.api.model.accounting.LedgerEntry;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.enums.AccountMappingBatchAction;
import br.com.condominioauditoria.api.model.enums.CashFlowRowCode;
import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.service.budget.BudgetScenario;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * RF-13.11 (ADR 0009, Decision 2): "Entradas e saídas" of the operating fund with synthetic entries; every outflow has
 * one destination and the cells add up to the debits of the cash flow.
 */
class CashFlowServiceTest {

    private final BudgetScenario scenario = new BudgetScenario();
    private Budget budget;
    private SourceFile file;

    @BeforeEach
    void prepare() {
        budget = scenario.confirmedBudget();
        file = scenario.cashFlow("fluxo-2026-09.pdf", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 4);
        LocalDate day = LocalDate.of(2026, 9, 10);
        entry("", "RECIBOS ACUMULADOS", "10000.00", "0.00", day, true);
        entry("1621", "MATERIAL HIDRÁULICO", "0.00", "4949.99", day, false);
        entry("1324", "ESTORNOS", "0.00", "100.00", day, false);
        entry("1999", "CONTA NOVA", "0.00", "50.01", day, false);
        scenario.accountMapping.loadSheet(scenario.condominiumId, budget.getId(), "mapa.csv", """
                1621;1.7.8
                1324;AJUSTE (estorno)
                """, "admin");
        scenario.accountMapping.batch(scenario.condominiumId, budget.getId(),
                new AccountMappingBatchRequest(AccountMappingBatchAction.CONFIRM, List.of("1621", "1324")), "admin");
    }

    private void entry(String account, String name, String credit, String debit, LocalDate date, boolean fee) {
        var read = new LedgerEntryData(1, scenario.ledgerEntries.size() + 1, date, account, name, "", "Teste " + name,
                new BigDecimal(credit), new BigDecimal(debit), BigDecimal.ZERO.setScale(2),
                new Enrichment(null, null, null, false, fee));
        scenario.ledgerEntries.add(new LedgerEntry(scenario.condominiumId, file.getId(),
                scenario.operatingFund.getId(), read));
    }

    @Test
    void beforeTheReviewTheOutflowsAreByAccount() {
        CashFlowMonthlyResponse r = scenario.cashFlow.monthly(scenario.condominiumId, null, null, null);

        CashFlowMonthResponse sep = r.months().getFirst();
        assertThat(sep.month()).isEqualTo("2026-09");
        assertThat(sep.byAccount()).isTrue();
        assertThat(sep.outflows()).isEqualByComparingTo("5100.00");
        assertThat(sep.inflows()).isEqualByComparingTo("10000.00");
        assertThat(sep.monthBalance()).isEqualByComparingTo("4900.00");
        assertThat(r.outflowGroups()).isEmpty();
        assertThat(r.warnings()).anyMatch(w -> w.code().equals("LINES_NOT_REVIEWED"));
    }

    @Test
    void afterTheReviewEachOutflowHasItsDestination() {
        scenario.reviewAllLines(budget);

        CashFlowMonthlyResponse r = scenario.cashFlow.monthly(scenario.condominiumId, null, null, null);

        CashFlowMonthResponse sep = r.months().getFirst();
        assertThat(sep.byAccount()).isFalse();
        assertThat(sep.outflows()).isEqualByComparingTo("5100.00");
        assertThat(sep.inLines()).isEqualByComparingTo("4949.99");
        assertThat(block(r, CashFlowRowCode.ADJUSTMENTS).total()).isEqualByComparingTo("100.00");
        assertThat(block(r, CashFlowRowCode.WITHOUT_BUDGET_LINE).total()).isEqualByComparingTo("50.01");
        var line = r.outflowGroups().stream().flatMap(g -> g.lines().stream())
                .filter(l -> "1.7.8".equals(l.lineCode())).findFirst().orElseThrow();
        assertThat(line.total()).isEqualByComparingTo("4949.99");
        // Lines without entries appear with 0,00
        assertThat(r.outflowGroups().stream().flatMap(g -> g.lines().stream())
                .filter(l -> !"1.7.8".equals(l.lineCode()))).isNotEmpty()
                .allSatisfy(l -> assertThat(l.total()).isEqualByComparingTo("0.00"));

        var entries = scenario.cashFlow.entries(scenario.condominiumId, null, "2026-09", line.lineId(), null, null,
                null, null);
        assertThat(entries.entries()).singleElement().satisfies(e -> {
            assertThat(e.amount()).isEqualByComparingTo("4949.99");
            assertThat(e.destination()).isEqualTo(CashFlowRowCode.BUDGET_LINE);
            assertThat(e.fileName()).isEqualTo("fluxo-2026-09.pdf");
        });
        var all = scenario.cashFlow.entries(scenario.condominiumId, null, "2026-09", null, null, null, null, null);
        assertThat(all.entries()).extracting(e -> e.direction() + ":" + e.destination())
                .containsExactlyInAnyOrder("IN:CONDO_FEE", "OUT:BUDGET_LINE", "OUT:ADJUSTMENTS",
                        "OUT:WITHOUT_BUDGET_LINE");
    }

    private static CashFlowRowResponse block(CashFlowMonthlyResponse r, CashFlowRowCode code) {
        return r.outflowBlocks().stream().filter(b -> b.code() == code).findFirst().orElseThrow();
    }
}
