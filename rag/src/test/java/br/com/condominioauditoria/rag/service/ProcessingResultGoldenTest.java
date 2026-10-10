package br.com.condominioauditoria.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import br.com.condominioauditoria.rag.messaging.FileReceivedMessage;
import br.com.condominioauditoria.rag.messaging.MessageContract;
import br.com.condominioauditoria.rag.parser.ReaderContract;
import br.com.condominioauditoria.rag.parser.budget.ProtestBudgetParser;
import br.com.condominioauditoria.rag.parser.cashflow.CashFlowParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * ProcessingResultMessage v2 messages of the private golden (September/2026 cash flow and 2026/2027 budget), in the
 * form the rag publishes them. They are the input of the api golden (ADR 0004, steps 6 and 7): the api never imports a
 * rag class, it only reads the message through the contract (contracts/mensagens/v3). The ids are fixed so the output
 * is always the same.
 *
 * Without the message file, the test writes it; with it, it checks that the rag's current output is identical (did
 * the reading change? delete the file, run again and run the api golden). Without the private golden, the test is
 * skipped.
 */
public class ProcessingResultGoldenTest {

    static final UUID CONDOMINIUM = UUID.fromString("00000000-0000-0000-0000-00000000c0d0");
    static final UUID CASH_FLOW_FILE = UUID.fromString("00000000-0000-0000-0000-0000000f1009");
    static final UUID BUDGET_FILE = UUID.fromString("00000000-0000-0000-0000-000000002627");

    private final FileProcessingService service = new FileProcessingService(new MessageContract(), null, null, null,
            new CashFlowParser(), new ProtestBudgetParser());

    @Test
    public void septemberCashFlow() throws Exception {
        checkOrWrite("fluxo-caixa-2026-09", CASH_FLOW_FILE, "TRIAL_BALANCE");
    }

    @Test
    public void fiscalYearBudget() throws Exception {
        checkOrWrite("po-2026-2027", BUDGET_FILE, "PO");
    }

    private void checkOrWrite(String name, UUID fileId, String category) throws Exception {
        Path privateDir = Path.of(System.getProperty("golden.dir"), "privado");
        Path read = privateDir.resolve(name + ".documento-lido.json");
        assumeTrue(Files.exists(read), "golden privado ausente");
        String sha = "0".repeat(64);
        var file = new FileReceivedMessage(3, fileId, fileId, CONDOMINIUM, category, name + ".pdf",
                "golden/" + name + ".pdf", sha);
        var result = service.parse(file, new ReaderContract().convert(Files.readString(read)));
        String json = new String(new MessageContract().write(result), StandardCharsets.UTF_8);

        Path message = privateDir.resolve(name + ".resultado-v3.json");
        if (!Files.exists(message)) {
            Files.writeString(message, json);
        }
        assertThat(Files.readString(message)).as("saída do rag para " + name).isEqualTo(json);
    }
}
