package br.com.condominioauditoria.rag.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.condominioauditoria.rag.model.TotalsCheck;
import br.com.condominioauditoria.rag.model.budget.Budget;
import br.com.condominioauditoria.rag.model.budget.BudgetLine;
import br.com.condominioauditoria.rag.model.cashflow.CashFlow;
import br.com.condominioauditoria.rag.model.cashflow.FundPosition;
import br.com.condominioauditoria.rag.model.cashflow.FundSection;
import br.com.condominioauditoria.rag.model.cashflow.LedgerEntry;
import br.com.condominioauditoria.rag.service.calculator.CashFlowCheck;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The rag consumes FileReceivedMessage v1 and publishes ProcessingResultMessage v2 (contracts/mensagens). */
public class MessageContractTest {

    private final MessageContract contract = new MessageContract();

    private static final FileReceivedMessage FILE = new FileReceivedMessage(3, UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), "TRIAL_BALANCE", "fluxo.pdf", "c/TRIAL_BALANCE/2026/abc-fluxo.pdf", "a".repeat(64));

    @Test
    public void cashFlowResultFollowsContractWithMoneyAsText() {
        var entry = new LedgerEntry(1, 1, LocalDate.of(2026, 9, 2), "3.1.01", "Água", "123", "CONTA DE ÁGUA",
                BigDecimal.ZERO.setScale(2), new BigDecimal("1500.10"), new BigDecimal("8499.90"),
                new LedgerEntry.Enrichment(null, "SABESP", null, false, false));
        var section = new FundSection("ORDINÁRIO", new BigDecimal("10000.00"), List.of(entry),
                BigDecimal.ZERO.setScale(2), new BigDecimal("1500.10"));
        var position = new FundPosition("ORDINÁRIO", new BigDecimal("10000.00"), BigDecimal.ZERO.setScale(2),
                new BigDecimal("1500.10"), new BigDecimal("8499.90"));
        var cashFlow = new CashFlow("MIO", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), List.of(section),
                List.of(position), position);

        byte[] json = contract.write(ProcessingResultMessage.completed(FILE, "fluxo-caixa-protest", 3, cashFlow,
                CashFlowCheck.check(cashFlow)));

        String text = new String(json, StandardCharsets.UTF_8);
        assertThat(text).contains("\"version\":3").contains("\"debit\":\"1500.10\"")
                .contains("\"status\":\"COMPLETED\"").contains("\"condoFeeReceipt\":false")
                .contains("\"budget\":null").doesNotContain("totalLancamentos");
    }

    @Test
    public void startedAndFailed() {
        assertThat(contract.write(ProcessingResultMessage.started(FILE))).isNotEmpty();
        assertThat(contract.write(ProcessingResultMessage.failed(FILE, "leitor fora do ar"))).isNotEmpty();
    }

    @Test
    public void fileReceivedOutsideContractIsRejected() {
        String json = """
                {"version":3,"fileId":"%s"}""".formatted(UUID.randomUUID());
        assertThatThrownBy(() -> contract.readFileReceived(json.getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("fora do contrato");
    }

    @Test
    public void validFileReceived() {
        String json = """
                {"version":3,"processingId":"%s","fileId":"%s","condominiumId":"%s","category":"TRIAL_BALANCE",
                 "originalName":"fluxo.pdf","path":"x/y.pdf","sha256":"%s"}"""
                .formatted(UUID.randomUUID(), FILE.fileId(), UUID.randomUUID(), "b".repeat(64));
        assertThat(contract.readFileReceived(json.getBytes(StandardCharsets.UTF_8)).fileId())
                .isEqualTo(FILE.fileId());
    }

    /**
     * The contract example (cash flow with condo fee receipts), which the api uses in its own test, is what the rag
     * produces.
     */
    @Test
    public void contractCashFlowExampleIsWhatRagProduces() throws Exception {
        var example = MAPPER.readTree(example("processing-result-cash-flow.json"));
        var water = new LedgerEntry(1, 1, LocalDate.of(2026, 9, 2), "3101", "ÁGUA", "123", "CONTA DE ÁGUA",
                new BigDecimal("0.00"), new BigDecimal("1500.10"), new BigDecimal("8499.90"),
                new LedgerEntry.Enrichment(null, "SABESP", null, false, false));
        var condoFee = new LedgerEntry(1, 2, LocalDate.of(2026, 9, 10), null, null, null, "RECIBOS ACUMULADOS",
                new BigDecimal("14260.79"), new BigDecimal("0.00"), new BigDecimal("64260.79"),
                new LedgerEntry.Enrichment(null, null, null, false, true));
        var operating = new FundSection("ORDINÁRIO", new BigDecimal("10000.00"), List.of(water),
                new BigDecimal("0.00"), new BigDecimal("1500.10"));
        var reserve = new FundSection("FUNDO DE RESERVA", new BigDecimal("50000.00"), List.of(condoFee),
                new BigDecimal("14260.79"), new BigDecimal("0.00"));
        var positions = List.of(
                new FundPosition("ORDINÁRIO", new BigDecimal("10000.00"), new BigDecimal("0.00"),
                        new BigDecimal("1500.10"), new BigDecimal("8499.90")),
                new FundPosition("FUNDO DE RESERVA", new BigDecimal("50000.00"), new BigDecimal("14260.79"),
                        new BigDecimal("0.00"), new BigDecimal("64260.79")));
        var total = new FundPosition("TOTAL", new BigDecimal("60000.00"), new BigDecimal("14260.79"),
                new BigDecimal("1500.10"), new BigDecimal("72760.69"));
        var cashFlow = new CashFlow("EXEMPLO", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                List.of(operating, reserve), positions, total);
        var check = new TotalsCheck("RUNNING_BALANCE", "Saldo linha a linha em todos os fundos", true,
                "2 lançamentos conferidos");

        byte[] produced = contract.write(ProcessingResultMessage.completed(fileOf(example), "fluxo-caixa-protest",
                1, cashFlow, List.of(check)));

        assertThat(MAPPER.readTree(produced)).isEqualTo(example);
    }

    /**
     * The budget-read example fits, field by field, into the rag's types (rag.model.budget) and comes back the same: no
     * contract field is left without a place and no extra field is published. Reading the budget itself is step 3 of
     * ADR 0004.
     */
    @Test
    public void contractBudgetExampleFitsRagTypes() throws Exception {
        String json = example("processing-result-budget.json");
        var example = MAPPER.readTree(json);
        var read = MAPPER.readValue(json, ProcessingResultMessage.class);

        Budget budget = read.budget();
        assertThat(budget.lines()).extracting(BudgetLine::printedCode).containsSubsequence("1.3.2",
                "1.3.2");
        var vacation = budget.lines().stream().filter(l -> l.printedCode().equals("1.1.5")).findFirst().orElseThrow();
        assertThat(vacation.budgeted()).isEqualByComparingTo("1585.14");
        assertThat(vacation.budgeted().scale()).isEqualTo(2);

        byte[] produced = contract.write(ProcessingResultMessage.completedBudget(fileOf(example), read.parser(),
                read.pages(), budget, read.totalsChecks()));

        assertThat(MAPPER.readTree(produced)).isEqualTo(example);
    }

    private static final tools.jackson.databind.json.JsonMapper MAPPER =
            tools.jackson.databind.json.JsonMapper.builder().build();

    private static String example(String name) throws Exception {
        return java.nio.file.Files.readString(java.nio.file.Path.of(System.getProperty("contratos.dir"),
                "mensagens/v3/examples", name));
    }

    private static FileReceivedMessage fileOf(tools.jackson.databind.JsonNode example) {
        return new FileReceivedMessage(3, UUID.fromString(example.get("processingId").asString()),
                UUID.fromString(example.get("fileId").asString()),
                UUID.fromString(example.get("condominiumId").asString()), "TRIAL_BALANCE", "f.pdf", "x/f.pdf",
                        "a".repeat(64));
    }
}
