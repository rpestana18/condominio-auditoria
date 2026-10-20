package br.com.condominioauditoria.rag.parser.cashflow;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.rag.model.TotalsCheck;
import br.com.condominioauditoria.rag.model.cashflow.CashFlow;
import br.com.condominioauditoria.rag.model.cashflow.LedgerEntry;
import br.com.condominioauditoria.rag.service.calculator.CashFlowCheck;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Cash flow parser with a hand-built page (positions of the pilot, no real data). The full golden test is
 * CashFlowParserGoldenTest.
 */
public class CashFlowParserTest {

    private final CashFlowParser parser = new CashFlowParser();

    /**
     * D1 of the RF-13 end-to-end test: a date in the memo ("... 30/03/2026 , NF: 34 DE:"), 3,75 pt below the entry
     * line, is memo text, not a new entry without balance.
     */
    @Test
    public void dateInsideMemoStaysInMemo() {
        var page = new SyntheticCashFlow()
                .fund(130, "CONDOMÍNIO", "1.000,00")
                .account(578.34, "1317.")
                .memo(582.09, "LOCACAO DE EQUIPAMENTOS")
                .entry(585.84, "06/04/2026", "ASSESSORAMENTO EM", "433726", null, "-", "150,00", "850,00")
                .memo(589.59, "30/03/2026 , NF: 34 DE:")
                .account(593.34, "ASSEMBLÉIAS")
                .memo(597.09, "TELAO TV E CIA")
                .entry(620, "07/04/2026", null, null, "RECIBOS ACUMULADOS", "50,00", "-", "900,00")
                .memo(627.5, "REPASSE CREDITO RECICLAGEM 25/03/2026")
                .totals(650, "50,00", "150,00")
                .financialPosition(680)
                .position(700, "CONDOMÍNIO", "1.000,00", "50,00", "150,00", "900,00")
                .position(712, "TOTAL", "1.000,00", "50,00", "150,00", "900,00");

        CashFlow cashFlow = parser.parse(page.document());

        assertThat(cashFlow.entryCount()).isEqualTo(2);
        LedgerEntry rental = cashFlow.sections().getFirst().entries().getFirst();
        assertThat(rental.date()).isEqualTo(LocalDate.of(2026, 4, 6));
        assertThat(rental.accountCode()).isEqualTo("1317");
        assertThat(rental.accountName()).isEqualTo("ASSESSORAMENTO EM ASSEMBLÉIAS");
        assertThat(rental.document()).isEqualTo("433726");
        assertThat(rental.memo()).isEqualTo("LOCACAO DE EQUIPAMENTOS 30/03/2026 , NF: 34 DE: TELAO TV E CIA");
        assertThat(rental.enrichment().invoiceNumber()).isEqualTo("34");
        assertThat(rental.enrichment().supplier()).isEqualTo("TELAO TV E CIA");
        assertThat(rental.debit()).isEqualByComparingTo("150.00");
        assertThat(rental.balance()).isEqualByComparingTo("850.00");

        LedgerEntry receipt = cashFlow.sections().getFirst().entries().get(1);
        assertThat(receipt.date()).isEqualTo(LocalDate.of(2026, 4, 7));
        assertThat(receipt.memo()).isEqualTo("RECIBOS ACUMULADOS REPASSE CREDITO RECICLAGEM 25/03/2026");
        assertThat(CashFlowCheck.check(cashFlow)).allSatisfy(c -> assertThat(c.ok()).as(c.code()).isTrue());
    }

    /** D2: the parser keeps the Posição Financeira as printed, even when a fund with nothing in it is missing. */
    @Test
    public void positionStaysAsPrinted() {
        CashFlow cashFlow = parser.parse(withEmptyFundOutsidePosition().document());

        assertThat(cashFlow.sections()).extracting(s -> s.fund()).containsExactly("CONDOMÍNIO", "OBRAS");
        assertThat(cashFlow.financialPosition()).extracting(p -> p.fund()).containsExactly("CONDOMÍNIO");
        assertThat(CashFlowCheck.check(cashFlow)).allMatch(TotalsCheck::ok);
    }

    /** Fund OBRAS with SALDO ANTERIOR 0,00, no entry and TOTAIS 0,00 / 0,00, left out of the Posição Financeira. */
    public static SyntheticCashFlow withEmptyFundOutsidePosition() {
        return new SyntheticCashFlow()
                .fund(130, "CONDOMÍNIO", "1.000,00")
                .entry(200, "07/04/2026", null, null, "RECIBOS ACUMULADOS", "50,00", "-", "1.050,00")
                .totals(220, "50,00", "0,00")
                .fund(260, "OBRAS", "0,00")
                .totals(320, "0,00", "0,00")
                .financialPosition(360)
                .position(380, "CONDOMÍNIO", "1.000,00", "50,00", "0,00", "1.050,00")
                .position(392, "TOTAL", "1.000,00", "50,00", "0,00", "1.050,00");
    }
}
