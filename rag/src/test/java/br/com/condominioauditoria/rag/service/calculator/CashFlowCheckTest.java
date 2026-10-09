package br.com.condominioauditoria.rag.service.calculator;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.rag.model.TotalsCheck;
import br.com.condominioauditoria.rag.model.cashflow.CashFlow;
import br.com.condominioauditoria.rag.model.cashflow.FundPosition;
import br.com.condominioauditoria.rag.model.cashflow.FundSection;
import br.com.condominioauditoria.rag.model.cashflow.LedgerEntry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

public class CashFlowCheckTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 1);

    private static LedgerEntry entry(String credit, String debit, String balance) {
        return new LedgerEntry(1, 1, DAY, "1062", "MATERIAL DE LIMPEZA", "1", "COMPRA",
                new BigDecimal(credit), new BigDecimal(debit), new BigDecimal(balance),
                new LedgerEntry.Enrichment(null, null, null, false, false));
    }

    private static CashFlow cashFlow(String printedBalance) {
        var section = new FundSection("CONDOMÍNIO", new BigDecimal("100.00"),
                List.of(entry("50.00", "0.00", "150.00"), entry("0.00", "30.00", printedBalance)),
                new BigDecimal("50.00"), new BigDecimal("30.00"));
        var position = new FundPosition("CONDOMÍNIO", new BigDecimal("100.00"), new BigDecimal("50.00"),
                new BigDecimal("30.00"), new BigDecimal("120.00"));
        return new CashFlow("TESTE", DAY, DAY.plusDays(29), List.of(section), List.of(position), position);
    }

    @Test
    public void consistentReportPassesEverything() {
        assertThat(CashFlowCheck.check(cashFlow("120.00"))).allMatch(TotalsCheck::ok);
    }

    @Test
    public void wrongPrintedBalanceIsPointedOutWithItsLine() {
        var result = CashFlowCheck.check(cashFlow("125.00"));

        TotalsCheck balance = result.getFirst();
        assertThat(balance.ok()).isFalse();
        assertThat(balance.detail()).contains("CONDOMÍNIO, pág. 1").contains("calculado 120,00, impresso 125,00");
    }
}
