package br.com.condominioauditoria.rag.parser.cashflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import br.com.condominioauditoria.rag.model.TotalsCheck;
import br.com.condominioauditoria.rag.model.cashflow.CashFlow;
import br.com.condominioauditoria.rag.model.cashflow.LedgerEntry;
import br.com.condominioauditoria.rag.parser.ReaderContract;
import br.com.condominioauditoria.rag.service.calculator.CashFlowCheck;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Real September/2026 cash flow of the pilot. The file is real data and stays out of git (data/golden/privado);
 * without it, the test is skipped.
 */
public class CashFlowParserGoldenTest {

    private static CashFlow cashFlow;

    @BeforeAll
    public static void read() throws Exception {
        Path json = Path.of(System.getProperty("golden.dir"), "privado/fluxo-caixa-2026-09.documento-lido.json");
        assumeTrue(Files.exists(json), "golden privado ausente");
        var document = new ReaderContract().convert(Files.readString(json));
        var parser = new CashFlowParser();
        assertThat(parser.recognizes(document)).isTrue();
        cashFlow = parser.parse(document);
    }

    @Test
    public void header() {
        assertThat(cashFlow.property()).isEqualTo("2815 - MIO RESIDENCIAL PARQUE");
        assertThat(cashFlow.periodStart()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(cashFlow.periodEnd()).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    public void allFundsAndEntries() {
        assertThat(cashFlow.sections()).hasSize(21);
        assertThat(cashFlow.financialPosition()).hasSize(21);
        assertThat(cashFlow.entryCount()).isEqualTo(423);
        assertThat(cashFlow.positionTotal().closingBalance()).isEqualByComparingTo("457051.86");
    }

    @Test
    public void allChecksPass() {
        assertThat(CashFlowCheck.check(cashFlow)).allSatisfy(v ->
                assertThat(v.ok()).as(v.code() + ": " + v.detail()).isTrue())
                .extracting(TotalsCheck::code)
                .containsExactly("SALDO_CORRENTE", "TOTAIS_FUNDO", "SALDO_FINAL_FUNDO", "TOTAL_POSICAO");
    }

    @Test
    public void memoDoesNotMixWithNeighbor() {
        LedgerEntry firstPurchase = cashFlow.sections().getFirst().entries().get(1);
        assertThat(firstPurchase.accountCode()).isEqualTo("1467");
        assertThat(firstPurchase.accountName()).isEqualTo("PEÇAS E ACESSÓRIOS");
        assertThat(firstPurchase.document()).isEqualTo("563946");
        assertThat(firstPurchase.memo())
                .isEqualTo("COMPRA DE TOLDO, RECIBO: DE: MERCADO PAGO INSTITUICAO DE PAGAMENTO LTDA");
        assertThat(firstPurchase.debit()).isEqualByComparingTo("1078.80");
        assertThat(firstPurchase.enrichment().paymentMethod()).isEqualTo("MERCADO PAGO");
    }

    @Test
    public void purchasesByPaymentMethodMatchManualAnalysis() {
        var purchases = cashFlow.sections().stream().flatMap(s -> s.entries().stream())
                .filter(l -> l.debit().signum() > 0)
                .filter(l -> Objects.equals(l.enrichment().paymentMethod(), "MERCADO PAGO"))
                .toList();
        assertThat(purchases).hasSize(35);
        assertThat(purchases.stream().map(LedgerEntry::debit).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("18215.37");
    }

    /** ADR 0004, Decision 7 and RF-03.1.9: fund collection = "RECIBOS ACUMULADOS" credits. */
    @Test
    public void fundCondoFeeReceiptsMatchManualAnalysis() {
        assertThat(condoFeeReceiptSum("FUNDO DE RESERVA")).isEqualByComparingTo("14260.79");
        assertThat(condoFeeReceiptSum("OBRAS / REFORMAS / INFRA")).isEqualByComparingTo("9705.06");
        assertThat(condoFeeReceiptSum("OBRAS")).isEqualByComparingTo("25.13");
    }

    @Test
    public void allReserveFundCreditsAreCondoFeeReceipts() {
        assertThat(section("FUNDO DE RESERVA").entries()).filteredOn(l -> l.credit().signum() != 0)
                .hasSize(24)
                .allSatisfy(l -> assertThat(l.enrichment().condoFeeReceipt()).isTrue());
    }

    @Test
    public void noDebitMarkedAsCondoFeeReceipt() {
        var marked = cashFlow.sections().stream().flatMap(s -> s.entries().stream())
                .filter(l -> l.enrichment().condoFeeReceipt()).toList();
        assertThat(marked).isNotEmpty().allSatisfy(l -> {
            assertThat(l.debit().signum()).isZero();
            assertThat(l.memo()).isEqualTo("RECIBOS ACUMULADOS");
        });
        assertThat(cashFlow.sections().stream().flatMap(s -> s.entries().stream())
                .filter(l -> l.debit().signum() != 0 && l.enrichment().condoFeeReceipt())).isEmpty();
    }

    private static br.com.condominioauditoria.rag.model.cashflow.FundSection section(String fund) {
        return cashFlow.sections().stream().filter(s -> s.fund().equals(fund)).findFirst().orElseThrow();
    }

    private static BigDecimal condoFeeReceiptSum(String fund) {
        return section(fund).entries().stream().filter(l -> l.enrichment().condoFeeReceipt())
                .map(LedgerEntry::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    public void accountOnTwoLines() {
        var issGreaseTrap = cashFlow.sections().getFirst().entries().stream()
                .filter(l -> "1465".equals(l.accountCode())).findFirst().orElseThrow();
        assertThat(issGreaseTrap.accountName()).isEqualTo("CAIXA GORDURA/FOSSA");
        assertThat(issGreaseTrap.enrichment().invoiceNumber()).isEqualTo("1002194");
    }

    @Test
    public void fullResultFitsQueueContract() {
        var file = new br.com.condominioauditoria.rag.messaging.FileReceivedMessage(1, java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), "BALANCETE", "fluxo.pdf", "x/fluxo.pdf",
                "a".repeat(64));
        byte[] json = new br.com.condominioauditoria.rag.messaging.MessageContract().write(
                br.com.condominioauditoria.rag.messaging.ProcessingResultMessage.completed(file,
                        "fluxo-caixa-protest", 30, cashFlow, CashFlowCheck.check(cashFlow)));
        assertThat(json.length).isGreaterThan(10_000);
    }
}
