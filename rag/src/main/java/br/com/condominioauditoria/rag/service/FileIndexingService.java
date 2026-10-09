package br.com.condominioauditoria.rag.service;

import br.com.condominioauditoria.rag.client.DocumentReaderClient;
import br.com.condominioauditoria.rag.config.QueueConfig;
import br.com.condominioauditoria.rag.indice.CortadorTrechos;
import br.com.condominioauditoria.rag.indice.DocumentoCortado;
import br.com.condominioauditoria.rag.indice.EmbeddingsIndisponiveisException;
import br.com.condominioauditoria.rag.indice.GeradorEmbeddings;
import br.com.condominioauditoria.rag.indice.RepositorioIndice;
import br.com.condominioauditoria.rag.indice.RepositorioIndice.DocumentoIndexado;
import br.com.condominioauditoria.rag.indice.TrechoCortado;
import br.com.condominioauditoria.rag.messaging.IndexFileMessage;
import br.com.condominioauditoria.rag.messaging.IndexingResultMessage;
import br.com.condominioauditoria.rag.messaging.MessageContract;
import br.com.condominioauditoria.rag.model.document.ReadDocument;
import br.com.condominioauditoria.storage.Storage;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

/**
 * Consumes the rag.indexacao queue (ADR 0003, Decision 5.1). INDEXAR: reports that it started, reads the original,
 * calls the reader, cuts it into chunks by location, generates the vectors in Ollama and stores everything in one
 * transaction, replacing the file's previous index. RETIRAR: logical deletion (nothing is deleted).
 *
 * Own queue, with its own parallelism: slow indexing or Ollama being down do not delay the accounting read. Ack only
 * after publishing the result; if the rag goes down midway, RabbitMQ delivers again and the api discards results of an
 * old indexacaoId.
 */
@Service
public class FileIndexingService {

    private static final Logger log = LoggerFactory.getLogger(FileIndexingService.class);

    private final MessageContract contract;
    private final RabbitTemplate rabbit;
    private final Storage storage;
    private final DocumentReaderClient reader;
    private final GeradorEmbeddings embeddings;
    private final RepositorioIndice repository;

    public FileIndexingService(MessageContract contract, RabbitTemplate rabbit, Storage storage,
            DocumentReaderClient reader, GeradorEmbeddings embeddings, RepositorioIndice repository) {
        this.contract = contract;
        this.rabbit = rabbit;
        this.storage = storage;
        this.reader = reader;
        this.embeddings = embeddings;
        this.repository = repository;
    }

    @RabbitListener(queues = QueueConfig.INDEXING, concurrency = "${rag.indexacao.paralelismo:1}")
    public void onReceive(Message message) {
        IndexFileMessage request = contract.readIndexFile(message.getBody());
        if (request.operation() == IndexFileMessage.Operation.RETIRAR) {
            boolean existed = repository.retirar(request.fileId(), request.indexingId());
            log.info("Arquivo {} retirado do índice{}", request.originalName(),
                    existed ? "" : " (não estava indexado)");
            publish(IndexingResultMessage.withdrawn(request));
            return;
        }
        Optional<IndexingResultMessage> alreadyDone = alreadyIndexed(request);
        if (alreadyDone.isPresent()) {
            repository.confirmarSemReindexar(request);
            log.info("Arquivo {} já indexado com o mesmo conteúdo, modelo e versão; só confirmado",
                    request.originalName());
            publish(alreadyDone.get());
            return;
        }
        repository.marcarIndexando(request);
        publish(IndexingResultMessage.indexing(request));
        IndexingResultMessage result;
        try {
            result = index(request);
            log.info("Arquivo {} indexado: {} ({} trechos)", request.originalName(), result.status(),
                    result.chunks());
        } catch (Exception error) {
            log.warn("Falha ao indexar {}: {}", request.originalName(), error.getMessage(), error);
            String reason = readableReason(error);
            repository.marcarErro(request.fileId(), request.indexingId(), reason);
            result = IndexingResultMessage.error(request, reason);
        }
        publish(result);
    }

    /**
     * Idempotency (ADR 0003, Q14): same file, sha256, model and indexer version = not redone. Turning the module back
     * on only indexes what is new or changed.
     */
    private Optional<IndexingResultMessage> alreadyIndexed(IndexFileMessage request) {
        Optional<DocumentoIndexado> current = repository.buscar(request.fileId());
        if (current.isEmpty() || !request.sha256().equals(current.get().sha256())
                || !CortadorTrechos.VERSAO.equals(current.get().versaoIndexador())) {
            return Optional.empty();
        }
        DocumentoIndexado doc = current.get();
        if ("sem_texto".equals(doc.estado())) { // sem texto não tem vetores: o modelo não importa
            return Optional.of(IndexingResultMessage.noText(request, doc.motivo(), doc.paginas(),
                    doc.versaoIndexador()));
        }
        if ("indexado".equals(doc.estado()) && Objects.equals(requestedModel(request), doc.modeloEmbeddings())) {
            return Optional.of(IndexingResultMessage.indexed(request, doc.paginas(), doc.trechos(),
                    doc.modeloEmbeddings(), doc.versaoIndexador()));
        }
        return Optional.empty();
    }

    /** Model that will generate the vectors; null when embeddings are off for the condominium. */
    private String requestedModel(IndexFileMessage request) {
        return request.withVectors() ? embeddings.modelo() : null;
    }

    private IndexingResultMessage index(IndexFileMessage request) throws Exception {
        if (request.withVectors() && !embeddings.aceita(request.embeddingModel())) {
            throw new IllegalArgumentException("Modelo de embeddings " + request.embeddingModel()
                    + " não está disponível neste rag (disponível: " + embeddings.modelo() + ")");
        }
        byte[] content;
        try (InputStream input = storage.open(request.path())) {
            content = input.readAllBytes();
        }
        ReadDocument document = reader.read(request.originalName(), content);
        DocumentoCortado cut = CortadorTrechos.cortar(document);
        if (cut.semTexto()) {
            repository.substituir(request, cut, null, null);
            return IndexingResultMessage.noText(request, cut.motivoSemTexto(), cut.paginas(),
                    CortadorTrechos.VERSAO);
        }
        List<float[]> vectors = null;
        String model = requestedModel(request);
        String warning = null;
        if (model != null) {
            try {
                vectors = embeddings.gerar(cut.trechos().stream().map(TrechoCortado::texto).toList());
            } catch (EmbeddingsIndisponiveisException error) {
                // Without vectors the file still goes into the keyword search. It is stored without a model, so the
                // next request with Ollama up is not treated as a repeat and generates the vectors.
                warning = truncate("Indexado só para a busca por palavra, sem busca por significado: "
                        + error.getMessage());
                log.warn("Arquivo {} indexado sem vetores: {}", request.originalName(), error.getMessage());
                model = null;
            }
        }
        repository.substituir(request, cut, vectors, model, warning);
        return IndexingResultMessage.indexed(request, cut.paginas(), cut.trechos().size(), model,
                CortadorTrechos.VERSAO, warning);
    }

    private void publish(IndexingResultMessage result) {
        var properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding("UTF-8");
        rabbit.send("", QueueConfig.INDEXING_RESULTS, new Message(contract.write(result), properties));
    }

    private static String readableReason(Exception error) {
        if (error instanceof EmbeddingsIndisponiveisException) {
            return truncate(error.getMessage());
        }
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        if (error instanceof ResourceAccessException) {
            return truncate("O leitor de documentos não respondeu. Ele está rodando? (" + message + ")");
        }
        return truncate(message);
    }

    private static String truncate(String message) {
        return message.length() > 1000 ? message.substring(0, 1000) + "…" : message;
    }
}
