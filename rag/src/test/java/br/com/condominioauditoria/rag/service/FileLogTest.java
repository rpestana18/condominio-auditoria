package br.com.condominioauditoria.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.logging.LogContext;
import br.com.condominioauditoria.rag.client.DocumentReaderClient;
import br.com.condominioauditoria.rag.messaging.MessageContract;
import br.com.condominioauditoria.rag.parser.budget.ProtestBudgetParser;
import br.com.condominioauditoria.rag.parser.budget.SupportSheetParser;
import br.com.condominioauditoria.rag.parser.cashflow.CashFlowParser;
import br.com.condominioauditoria.rag.repository.IndexRepository;
import br.com.condominioauditoria.rag.search.EmbeddingGenerator;
import br.com.condominioauditoria.storage.Storage;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.web.client.ResourceAccessException;

/**
 * ADR 0008 (B1, E1, B9): the rag logs a file by its fileId, with the reason of a failure and the request id of the
 * queue, never by the original name (free text of the user, which may carry a name or a CPF).
 */
@ExtendWith(OutputCaptureExtension.class)
class FileLogTest {

    private static final String NAME = "Recibo Fulano 123.456.789-00.pdf";
    private static final String FILE_ID = "5a1c9e3b-2f4d-4b8a-8c7e-1d2f3a4b5c6d";

    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final Storage storage = mock(Storage.class);
    private final DocumentReaderClient reader = mock(DocumentReaderClient.class);
    private final List<String> requestIdsWhenPublishing = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        when(storage.open(anyString())).thenAnswer(i -> new ByteArrayInputStream(new byte[] {1}));
        when(reader.read(anyString(), any())).thenThrow(new ResourceAccessException("Connection refused"));
        doAnswer(i -> requestIdsWhenPublishing.add(MDC.get(LogContext.REQUEST_ID))).when(rabbit)
                .send(eq(""), anyString(), any(Message.class));
    }

    @Test
    void aFailedReadIsLoggedByFileIdWithTheReasonAndWithoutTheName(CapturedOutput output) throws Exception {
        var service = new FileProcessingService(new MessageContract(), rabbit, storage, reader, new CashFlowParser(),
                new ProtestBudgetParser(), new SupportSheetParser());

        service.onReceive(message(fileReceived()));

        assertThat(output.getOut()).contains("WARN").contains("Leitura do arquivo " + FILE_ID + " falhou")
                .contains("O leitor de documentos não respondeu").doesNotContain("Recibo")
                .doesNotContain("123.456.789-00");
        // STARTED and FAILED go out with the request id received in the AMQP header
        assertThat(requestIdsWhenPublishing).containsExactly("req-rag", "req-rag");
        assertThat(MDC.get(LogContext.REQUEST_ID)).isNull();
    }

    @Test
    void aFailedIndexingIsLoggedByFileIdWithoutTheName(CapturedOutput output) throws Exception {
        IndexRepository repository = mock(IndexRepository.class);
        when(repository.find(any())).thenReturn(Optional.empty());
        EmbeddingGenerator embeddings = mock(EmbeddingGenerator.class);
        when(embeddings.accepts(any())).thenReturn(true);
        var service = new FileIndexingService(new MessageContract(), rabbit, storage, reader, embeddings, repository);
        String json = Files.readString(Path.of(System.getProperty("contratos.dir"),
                "mensagens/v3/examples/index-file-index.json")).replace("ata-assembleia-2026-09.pdf", NAME);

        service.onReceive(message(json));

        assertThat(output.getOut()).contains("Indexação do arquivo " + FILE_ID + " falhou").doesNotContain("Recibo");
        assertThat(requestIdsWhenPublishing).containsOnly("req-rag");
    }

    /** B9: the indexing parallelism is read from the key of application.yml (RAG_PARALELISMO_INDEXACAO). */
    @Test
    void indexingParallelismReadsTheKeyOfTheConfiguration() throws Exception {
        Method listener = FileIndexingService.class.getMethod("onReceive", Message.class);
        String concurrency = listener.getAnnotation(RabbitListener.class).concurrency();
        assertThat(concurrency).isEqualTo("${rag.indexing.parallelism:1}");
        String yml = Files.readString(Path.of("src/main/resources/application.yml"));
        assertThat(yml).contains("  indexing:").contains("parallelism: ${RAG_PARALELISMO_INDEXACAO:1}");
    }

    private static String fileReceived() {
        return """
                {"version":3,"processingId":"0b1c2d3e-4f50-4617-8293-a4b5c6d7e8f9","fileId":"%s",
                 "condominiumId":"6f1d2c1e-3b4a-4c8e-9a51-2815a0000001","category":"TRIAL_BALANCE",
                 "originalName":"%s","path":"6f1d/BALANCETE/2026/abc-%s","sha256":"%s"}"""
                .formatted(FILE_ID, NAME, NAME, "a".repeat(64));
    }

    private static Message message(String json) {
        MessageProperties properties = new MessageProperties();
        properties.setHeader(LogContext.AMQP_HEADER, "req-rag");
        return new Message(json.getBytes(StandardCharsets.UTF_8), properties);
    }
}
