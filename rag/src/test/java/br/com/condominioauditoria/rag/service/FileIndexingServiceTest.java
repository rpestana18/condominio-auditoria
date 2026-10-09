package br.com.condominioauditoria.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.rag.client.DocumentReaderClient;
import br.com.condominioauditoria.rag.config.QueueConfig;
import br.com.condominioauditoria.rag.indice.DocumentoCortado;
import br.com.condominioauditoria.rag.indice.EmbeddingsIndisponiveisException;
import br.com.condominioauditoria.rag.indice.GeradorEmbeddings;
import br.com.condominioauditoria.rag.indice.RepositorioIndice;
import br.com.condominioauditoria.rag.indice.RepositorioIndice.DocumentoIndexado;
import br.com.condominioauditoria.rag.messaging.MessageContract;
import br.com.condominioauditoria.rag.model.document.ReadDocument;
import br.com.condominioauditoria.storage.Storage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Ollama down during indexing: the file goes into the keyword search and the next request generates the vectors. */
public class FileIndexingServiceTest {

    private static final UUID FILE = UUID.fromString("5a1c9e3b-2f4d-4b8a-8c7e-1d2f3a4b5c6d");
    private static final String SHA = "a".repeat(64);

    private final MessageContract contract = new MessageContract();
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final Storage storage = mock(Storage.class);
    private final DocumentReaderClient reader = mock(DocumentReaderClient.class);
    private final GeradorEmbeddings embeddings = mock(GeradorEmbeddings.class);
    private final RepositorioIndice repository = mock(RepositorioIndice.class);
    private final FileIndexingService service = new FileIndexingService(contract, rabbit, storage, reader,
            embeddings, repository);

    @BeforeEach
    public void setUp() throws Exception {
        when(embeddings.modelo()).thenReturn("bge-m3");
        when(embeddings.aceita(any())).thenReturn(true);
        when(storage.open(anyString())).thenAnswer(i -> new ByteArrayInputStream(new byte[] {1}));
        when(reader.read(anyString(), any())).thenReturn(new ReadDocument("1", "t",
                new ReadDocument.FileInfo("ata.pdf", SHA, 1), "docx", List.of(), List.of(),
                List.of(new ReadDocument.Paragraph(1, "Multa de 2% por atraso", null))));
    }

    @Test
    public void ollamaDownStoresChunksWithoutVectorAndWarns() throws Exception {
        when(repository.buscar(FILE)).thenReturn(Optional.empty());
        when(embeddings.gerar(anyList())).thenThrow(new EmbeddingsIndisponiveisException("Ollama fora do ar", null));

        service.onReceive(request());

        ArgumentCaptor<DocumentoCortado> cut = ArgumentCaptor.forClass(DocumentoCortado.class);
        verify(repository).substituir(any(), cut.capture(), isNull(), isNull(), eq(
                "Indexado só para a busca por palavra, sem busca por significado: Ollama fora do ar"));
        assertThat(cut.getValue().trechos()).hasSize(1);
        verify(repository, never()).marcarErro(any(), any(), any());
        List<JsonNode> published = published(2);
        assertThat(published.get(0).get("situacao").asString()).isEqualTo("INDEXANDO");
        assertThat(published.get(1).get("situacao").asString()).isEqualTo("INDEXADO");
        assertThat(published.get(1).get("modeloEmbeddings").isNull()).isTrue();
        assertThat(published.get(1).get("trechos").asInt()).isEqualTo(1);
        assertThat(published.get(1).get("motivo").asString()).contains("Ollama fora do ar");
    }

    @Test
    public void indexedWithoutVectorIsReindexedWithBgeM3() throws Exception {
        when(repository.buscar(FILE)).thenReturn(Optional.of(new DocumentoIndexado(FILE, SHA, "indexado",
                "Indexado só para a busca por palavra", 1, 1, null, "1", false)));
        when(embeddings.gerar(anyList())).thenReturn(List.of(new float[1024]));

        service.onReceive(request());

        verify(repository, never()).confirmarSemReindexar(any());
        verify(repository).substituir(any(), any(), anyList(), eq("bge-m3"), isNull());
        assertThat(published(2).get(1).get("modeloEmbeddings").asString()).isEqualTo("bge-m3");
    }

    @Test
    public void indexedWithBgeM3IsSkipped() throws Exception {
        when(repository.buscar(FILE)).thenReturn(Optional.of(new DocumentoIndexado(FILE, SHA, "indexado",
                null, 1, 1, "bge-m3", "1", false)));

        service.onReceive(request());

        verify(repository).confirmarSemReindexar(any());
        verify(repository, never()).substituir(any(), any(), any(), any(), any());
        assertThat(published(1).getFirst().get("situacao").asString()).isEqualTo("INDEXADO");
    }

    private List<JsonNode> published(int count) {
        ArgumentCaptor<Message> messages = ArgumentCaptor.forClass(Message.class);
        verify(rabbit, times(count)).send(eq(""), eq(QueueConfig.INDEXING_RESULTS), messages.capture());
        var mapper = JsonMapper.builder().build();
        return messages.getAllValues().stream().map(m -> mapper.readTree(m.getBody())).toList();
    }

    private static Message request() throws Exception {
        byte[] json = Files.readAllBytes(Path.of(System.getProperty("contratos.dir"),
                "mensagens/v1/exemplos/indexar-arquivo-indexar.json"));
        return new Message(json, new MessageProperties());
    }
}
