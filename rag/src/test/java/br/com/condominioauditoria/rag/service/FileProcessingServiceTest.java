package br.com.condominioauditoria.rag.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.rag.messaging.FileReceivedMessage;
import br.com.condominioauditoria.rag.messaging.MessageContract;
import br.com.condominioauditoria.rag.messaging.ProcessingResultMessage;
import br.com.condominioauditoria.rag.messaging.ProcessingResultMessage.Status;
import br.com.condominioauditoria.rag.model.document.ReadDocument;
import br.com.condominioauditoria.rag.model.document.ReadDocument.Page;
import br.com.condominioauditoria.rag.parser.ReaderContract;
import br.com.condominioauditoria.rag.parser.budget.ProtestBudgetParser;
import br.com.condominioauditoria.rag.parser.cashflow.CashFlowParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The parser is chosen by the document content; the category only explains the scanned budget. */
public class FileProcessingServiceTest {

    private final FileProcessingService service = new FileProcessingService(new MessageContract(), null, null, null,
            new CashFlowParser(), new ProtestBudgetParser());

    @Test
    public void scannedBudgetFailsWithReadableReason() {
        ProcessingResultMessage r = service.parse(file("PO"), scanned());

        assertThat(r.status()).isEqualTo(Status.FALHOU);
        assertThat(r.reason()).isEqualTo("PO sem texto; OCR ainda não disponível");
        assertThat(new MessageContract().write(r)).isNotEmpty();
    }

    @Test
    public void otherScannedFileStaysWithoutParser() {
        ProcessingResultMessage r = service.parse(file("CONTRATO"), scanned());

        assertThat(r.status()).isEqualTo(Status.CONCLUIDO);
        assertThat(r.parser()).isNull();
        assertThat(r.budget()).isNull();
        assertThat(r.cashFlow()).isNull();
    }

    /**
     * With the private golden: the real budget comes out as a budget in v2, and the real cash flow still comes out as a
     * cash flow.
     */
    @Test
    public void realBudgetAndCashFlowByContent() throws Exception {
        Path privateDir = Path.of(System.getProperty("golden.dir"), "privado");
        Path budget = privateDir.resolve("po-2026-2027.documento-lido.json");
        if (Files.exists(budget)) {
            ProcessingResultMessage r = service.parse(file("PO"),
                    new ReaderContract().convert(Files.readString(budget)));
            assertThat(r.parser()).isEqualTo("po-protest");
            assertThat(r.budget().lines()).hasSize(98);
            assertThat(r.cashFlow()).isNull();
            assertThat(r.totalsChecks()).isNotEmpty();
            assertThat(new String(new MessageContract().write(r), java.nio.charset.StandardCharsets.UTF_8))
                    .contains("\"versao\":2").contains("\"orcado\":\"1585.14\"");
        }
        Path cashFlow = privateDir.resolve("fluxo-caixa-2026-09.documento-lido.json");
        if (Files.exists(cashFlow)) {
            ProcessingResultMessage r = service.parse(file("BALANCETE"),
                    new ReaderContract().convert(Files.readString(cashFlow)));
            assertThat(r.parser()).isEqualTo("fluxo-caixa-protest");
            assertThat(r.budget()).isNull();
            assertThat(r.cashFlow().entryCount()).isEqualTo(423);
            assertThat(new String(new MessageContract().write(r), java.nio.charset.StandardCharsets.UTF_8))
                    .contains("\"recebimentoCota\":true");
        }
    }

    private static ReadDocument scanned() {
        return new ReadDocument("1", "leitor-py", new ReadDocument.FileInfo("x.pdf", "a".repeat(64), 10), "pdf",
                List.of(new Page(1, 595, 842, "sem_texto", List.of())), List.of(), List.of());
    }

    private static FileReceivedMessage file(String category) {
        return new FileReceivedMessage(1, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), category, "x.pdf",
                "c/x.pdf", "a".repeat(64));
    }
}
