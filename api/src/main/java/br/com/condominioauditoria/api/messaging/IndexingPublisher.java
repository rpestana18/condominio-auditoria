package br.com.condominioauditoria.api.messaging;

import br.com.condominioauditoria.api.config.QueueConfig;
import br.com.condominioauditoria.api.config.properties.ApiProperties;
import br.com.condominioauditoria.api.event.FileIndexRequested;
import br.com.condominioauditoria.api.event.FilesIndexRequested;
import br.com.condominioauditoria.api.model.enums.IndexingStatus;
import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.modulo.Modulos;
import br.com.condominioauditoria.api.repository.file.SourceFileRepository;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Asks the rag to index a file for the document search, through the rag.indexacao queue (ADR 0003, Decision 5.1). Same
 * pattern as {@link FilePublisher}: the message only goes out after the commit; the sweep (every minute) resends what
 * has been queued or indexing for longer than the limit and, after too many attempts, marks it as an error with the
 * reason. Resending is safe: the api only applies the result of the current indexingId, and the rag does not redo an
 * unchanged index.
 *
 * Assistant feature disabled for the condominium (RF-10.3): no message goes out, not even from the sweep; the stalled
 * request stays as it is (without counting an attempt) until the feature is enabled again, when every file gets a new
 * request (RF-10.4).
 */
@Component
public class IndexingPublisher {

    private static final Logger log = LoggerFactory.getLogger(IndexingPublisher.class);
    private static final List<IndexingStatus> IN_PROGRESS = List.of(IndexingStatus.NA_FILA,
            IndexingStatus.INDEXANDO);

    private final RabbitTemplate rabbit;
    private final MessageContract contract;
    private final SourceFileRepository files;
    private final TransactionTemplate transaction;
    private final Modulos features;
    private final Duration resendAfter;
    private final int maxAttempts;
    /** Sends the batches (enabling the feature) outside the request thread, one batch at a time. */
    private final Executor batches;

    @Autowired
    IndexingPublisher(RabbitTemplate rabbit, MessageContract contract, SourceFileRepository files,
            PlatformTransactionManager transactionManager, ApiProperties properties, Modulos features) {
        this(rabbit, contract, files, transactionManager, properties, features,
                Executors.newSingleThreadExecutor(Thread.ofVirtual().name("indexacao-lote-", 0).factory()));
    }

    IndexingPublisher(RabbitTemplate rabbit, MessageContract contract, SourceFileRepository files,
            PlatformTransactionManager transactionManager, ApiProperties properties, Modulos features,
            Executor batches) {
        this.rabbit = rabbit;
        this.contract = contract;
        this.files = files;
        this.transaction = new TransactionTemplate(transactionManager);
        this.features = features;
        this.resendAfter = Duration.ofMinutes(properties.processing().resendAfterMinutes());
        this.maxAttempts = properties.processing().maxAttempts();
        this.batches = batches;
    }

    @PreDestroy
    void shutdown() {
        if (batches instanceof ExecutorService executor) {
            executor.shutdown();
        }
    }

    @TransactionalEventListener
    void afterCommit(FileIndexRequested event) {
        files.findById(event.fileId()).ifPresent(this::send);
    }

    @TransactionalEventListener
    void afterCommit(FilesIndexRequested batch) {
        batches.execute(() -> {
            try {
                if (!features.ligado(batch.condominiumId(), Modulos.ASSISTENTE)) {
                    return; // disabled again before sending
                }
                for (SourceFile file : files.findAllById(batch.fileIds())) {
                    publish(file);
                }
                log.info("Pedida a indexação de {} arquivo(s) do condomínio {}", batch.fileIds().size(),
                        batch.condominiumId());
            } catch (RuntimeException error) {
                log.warn("Falha ao enviar o lote de indexação do condomínio {}; a varredura reenvia: {}",
                        batch.condominiumId(), error.getMessage());
            }
        });
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    void periodically() {
        sweep(Instant.now().minus(resendAfter));
    }

    /** Resends what stalled before the cutoff; after too many attempts, marks it as an error with the reason. */
    void sweep(Instant cutoff) {
        List<SourceFile> stalled = transaction.execute(t -> {
            Map<UUID, Boolean> enabled = new HashMap<>();
            List<SourceFile> list = files.findByIndexingStatusInAndIndexingQueuedAtBeforeOrderByUploadedAt(
                    IN_PROGRESS, cutoff).stream()
                    .filter(a -> enabled.computeIfAbsent(a.getCondominiumId(),
                            c -> features.ligado(c, Modulos.ASSISTENTE)))
                    .toList();
            List<SourceFile> toResend = list.stream().filter(a -> a.getIndexingAttempts() < maxAttempts).toList();
            list.stream().filter(a -> a.getIndexingAttempts() >= maxAttempts).forEach(a -> a.failIndexing(
                    "A indexação não terminou depois de " + a.getIndexingAttempts()
                            + " tentativas. O serviço rag está rodando?"));
            toResend.forEach(SourceFile::resendIndexing);
            return toResend;
        });
        if (stalled != null && !stalled.isEmpty()) {
            log.info("Reenviando {} arquivo(s) parado(s) para indexação", stalled.size());
            stalled.forEach(this::publish); // the list is already filtered by the enabled feature
        }
    }

    /** Single request (upload, reprocess): checks the feature again, since it may have been disabled midway. */
    private void send(SourceFile file) {
        if (features.ligado(file.getCondominiumId(), Modulos.ASSISTENTE)) {
            publish(file);
        }
    }

    private void publish(SourceFile file) {
        if (file.getIndexingId() == null) {
            return;
        }
        var properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding("UTF-8");
        try {
            rabbit.send("", QueueConfig.INDEXING,
                    new Message(contract.write(IndexFileMessage.index(file)), properties));
        } catch (AmqpException error) {
            // The request stays queued; the sweep tries again
            log.warn("Fila indisponível ao pedir a indexação de {}: {}", file.getOriginalName(), error.getMessage());
        }
    }
}
