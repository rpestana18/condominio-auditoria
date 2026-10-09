package br.com.condominioauditoria.api.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.BudgetLineData;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.BudgetLineMark;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.BudgetLineType;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.LedgerEntryData;
import br.com.condominioauditoria.api.model.enums.FileCategory;
import br.com.condominioauditoria.api.model.file.SourceFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

/**
 * The api understands the v2 contract examples (the same ones the rag produces), rejects v1 and only sends valid
 * messages.
 */
class MessageContractTest {

    private final MessageContract contract = new MessageContract();

    static byte[] example(String version, String name) throws Exception {
        return Files.readAllBytes(Path.of(System.getProperty("contratos.dir"), "mensagens", version, "exemplos", name));
    }

    @Test
    void readsTheCashFlowExampleWithCondoFeeReceipt() throws Exception {
        ProcessingResultMessage r = contract.readProcessingResult(example("v2", "resultado-concluido-fluxo.json"));

        assertThat(r.version()).isEqualTo(2);
        assertThat(r.status()).isEqualTo(ProcessingResultMessage.Status.CONCLUIDO);
        assertThat(r.budget()).isNull();
        assertThat(r.cashFlow().entryCount()).isEqualTo(2);
        var water = r.cashFlow().sections().getFirst().entries().getFirst();
        assertThat(water.debit()).isEqualByComparingTo("1500.10");
        assertThat(water.debit().scale()).isEqualTo(2);
        assertThat(water.enrichment().supplier()).isEqualTo("SABESP");
        assertThat(water.enrichment().condoFeeReceipt()).isFalse();
        LedgerEntryData condoFee = r.cashFlow().sections().get(1).entries().getFirst();
        assertThat(condoFee.memo()).isEqualTo("RECIBOS ACUMULADOS");
        assertThat(condoFee.credit()).isEqualByComparingTo("14260.79");
        assertThat(condoFee.enrichment().condoFeeReceipt()).isTrue();
    }

    @Test
    void readsTheBudgetExample() throws Exception {
        ProcessingResultMessage r = contract.readProcessingResult(example("v2", "resultado-concluido-po.json"));

        assertThat(r.cashFlow()).isNull();
        var budget = r.budget();
        assertThat(budget.printedFiscalYear()).isEqualTo("2026 / 2027");
        assertThat(budget.budgetColumns()).containsExactly("2025/2026", "2026/2027");
        assertThat(budget.lines().getFirst().type()).isEqualTo(BudgetLineType.TOTAL);
        assertThat(budget.lines().getFirst().budgeted()).isEqualByComparingTo("474201.13");
        BudgetLineData buildingManager = line(budget, "1.3.20");
        assertThat(buildingManager.account()).isEqualTo("1682 - Sindicatura Profissional");
        assertThat(buildingManager.previousBudgeted()).isEqualByComparingTo("17195.00");
        assertThat(buildingManager.budgeted()).isEqualByComparingTo("8000.00");
        assertThat(buildingManager.budgeted().scale()).isEqualTo(2);
        assertThat(buildingManager.percentageText()).isEqualTo("-53,47%");
        assertThat(line(budget, "1.4.3").mark()).isEqualTo(BudgetLineMark.RATEIO_A_PARTE);
        assertThat(line(budget, "1.4.3").account()).isNull();
        assertThat(line(budget, "1.4.3").accountText()).isEqualTo("Débito em receitas eventuais");
        assertThat(line(budget, "1.9.1").accountText()).isEqualTo("Fundo de Reserva");
        assertThat(budget.lines().stream().filter(l -> l.printedCode().equals("1.3.2"))).hasSize(2);
        assertThat(r.totalsChecks()).anySatisfy(c -> {
            assertThat(c.code()).isEqualTo("CODIGO_REPETIDO");
            assertThat(c.ok()).isFalse();
        });
    }

    @Test
    void v1MessageIsRejected() throws Exception {
        assertThatThrownBy(() -> contract.readProcessingResult(example("v1", "resultado-concluido.json")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fora do contrato");
    }

    @Test
    void entryWithoutCondoFeeReceiptIsRejected() throws Exception {
        String json = new String(example("v2", "resultado-concluido-fluxo.json"), StandardCharsets.UTF_8)
                .replaceAll(",\\s*\"recebimentoCota\": false", "");
        assertThat(json).doesNotContain("\"recebimentoCota\": false");
        assertThatThrownBy(() -> contract.readProcessingResult(json.getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("fora do contrato");
    }

    @Test
    void unknownBudgetMarkIsRejected() throws Exception {
        String json = new String(example("v2", "resultado-concluido-po.json"), StandardCharsets.UTF_8)
                .replaceFirst("\"RATEIO_A_PARTE\"", "\"Rateio à parte\"");
        assertThatThrownBy(() -> contract.readProcessingResult(json.getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("fora do contrato");
    }

    @Test
    void failedWithoutReasonIsRejected() {
        String json = """
                {"versao":2,"processamentoId":"%s","arquivoId":"%s","condominioId":"%s","situacao":"FALHOU"}"""
                .formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertThatThrownBy(() -> contract.readProcessingResult(json.getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("fora do contrato");
    }

    @Test
    void moneyAsNumberIsRejected() throws Exception {
        String cashFlow = new String(example("v2", "resultado-concluido-fluxo.json"), StandardCharsets.UTF_8)
                .replace("\"1500.10\"", "1500.1");
        assertThatThrownBy(() -> contract.readProcessingResult(cashFlow.getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("fora do contrato");
        String budget = new String(example("v2", "resultado-concluido-po.json"), StandardCharsets.UTF_8)
                .replace("\"1585.14\"", "1585.14");
        assertThatThrownBy(() -> contract.readProcessingResult(budget.getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("fora do contrato");
    }

    @Test
    void readRequestFollowsTheContract() {
        var file = new SourceFile(UUID.randomUUID(), FileCategory.BALANCETE, "fluxo.pdf", "c/BALANCETE/2026/x-fluxo.pdf",
                "c".repeat(64), 10, "application/pdf", "gestor");
        String json = new String(contract.write(FileReceivedMessage.from(file)), StandardCharsets.UTF_8);
        assertThat(json).contains("\"versao\":1").contains("\"categoria\":\"BALANCETE\"")
                .contains(file.getProcessingId().toString());
    }

    private static BudgetLineData line(ProcessingResultMessage.BudgetData budget, String code) {
        return budget.lines().stream().filter(l -> l.printedCode().equals(code)).findFirst().orElseThrow();
    }

    // ---- indexing (ADR 0003, Decision 5.1) ----

    @Test
    void readsTheIndexedExample() throws Exception {
        IndexingResultMessage r = contract.readIndexingResult(example("resultado-indexacao-indexado.json"));

        assertThat(r.status()).isEqualTo(IndexingResultMessage.Status.INDEXADO);
        assertThat(r.indexingId()).isEqualTo(UUID.fromString("3c9d1e2f-4a5b-4c6d-8e7f-9a0b1c2d3e4f"));
        assertThat(r.pages()).isEqualTo(12);
        assertThat(r.chunks()).isEqualTo(15);
        assertThat(r.indexerVersion()).isEqualTo("1");
    }

    @Test
    void readsTheNoTextExample() throws Exception {
        IndexingResultMessage r = contract.readIndexingResult(example("resultado-indexacao-sem-texto.json"));

        assertThat(r.status()).isEqualTo(IndexingResultMessage.Status.SEM_TEXTO);
        assertThat(r.reason()).contains("sem texto extraível");
        assertThat(r.chunks()).isZero();
    }

    @Test
    void indexedWithoutCountsIsRejected() {
        String json = """
                {"versao":1,"indexacaoId":"%s","arquivoId":"%s","condominioId":"%s","situacao":"INDEXADO"}"""
                .formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertThatThrownBy(() -> contract.readIndexingResult(json.getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("fora do contrato");
    }

    @Test
    void indexingErrorWithoutReasonIsRejected() {
        String json = """
                {"versao":1,"indexacaoId":"%s","arquivoId":"%s","condominioId":"%s","situacao":"ERRO","motivo":""}"""
                .formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertThatThrownBy(() -> contract.readIndexingResult(json.getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("fora do contrato");
    }

    @Test
    void minimalIndexingIsAccepted() {
        String json = """
                {"versao":1,"indexacaoId":"%s","arquivoId":"%s","condominioId":"%s","situacao":"INDEXANDO"}"""
                .formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertThat(contract.readIndexingResult(json.getBytes(StandardCharsets.UTF_8)).status())
                .isEqualTo(IndexingResultMessage.Status.INDEXANDO);
    }

    /** The contract's request examples fit the api record and come out the same, and valid. */
    @ParameterizedTest
    @ValueSource(strings = {"indexar-arquivo-indexar.json", "indexar-arquivo-retirar.json"})
    void indexingRequestExampleRoundTrips(String name) throws Exception {
        var mapper = JsonMapper.builder().build();
        IndexFileMessage request = mapper.readValue(example(name), IndexFileMessage.class);

        String json = new String(contract.write(request), StandardCharsets.UTF_8);

        assertThat(mapper.readTree(json)).isEqualTo(mapper.readTree(example(name)));
    }

    @Test
    void indexingRequestFollowsTheContract() {
        var file = new SourceFile(UUID.randomUUID(), FileCategory.ATA, "ata.pdf", "c/ATA/2026/x-ata.pdf",
                "a".repeat(64), 10, "application/pdf", "gestor");
        file.requestIndexing();
        String json = new String(contract.write(IndexFileMessage.index(file)), StandardCharsets.UTF_8);
        assertThat(json).contains("\"operacao\":\"INDEXAR\"").contains("\"categoria\":\"ATA\"")
                .contains(file.getIndexingId().toString());
        // Each request has its own id, different from the read's id
        assertThat(file.getIndexingId()).isNotEqualTo(file.getProcessingId());
    }

    @Test
    void requestOutsideTheContractIsNotSent() {
        var file = new SourceFile(UUID.randomUUID(), FileCategory.ATA, "ata.pdf", "c/ATA/2026/x-ata.pdf",
                "hash-invalido", 10, "application/pdf", "gestor");
        assertThatThrownBy(() -> contract.write(IndexFileMessage.index(file)))
                .hasMessageContaining("fora do contrato");
    }

    private static byte[] example(String name) throws Exception {
        return Files.readAllBytes(Path.of(System.getProperty("contratos.dir"), "mensagens/v1/exemplos", name));
    }
}
