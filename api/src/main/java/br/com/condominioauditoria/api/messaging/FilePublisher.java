package br.com.condominioauditoria.api.messaging;

import br.com.condominioauditoria.api.config.QueueConfig;
import br.com.condominioauditoria.api.config.properties.ApiProperties;
import br.com.condominioauditoria.api.event.FileReadRequested;
import br.com.condominioauditoria.api.model.enums.FileStatus;
import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.repository.file.SourceFileRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Asks the rag, through the queue, to read a file. The message only goes out after the commit that records the file.
 *
 * The queues are durable: if the rag or the api restart, the messages wait for them. And nothing stays stalled forever:
 * if RabbitMQ is down when sending, or a read gets lost on the way, the sweep (every minute) resends what has been
 * Pending or Processing for longer than the limit. Resending is safe: the api applies only one result per processingId
 * and saving the result deletes the previous extraction before inserting.
 */
@Component
public class FilePublisher {

    private static final Logger log = LoggerFactory.getLogger(FilePublisher.class);
    private static final List<FileStatus> IN_PROGRESS = List.of(FileStatus.PENDENTE, FileStatus.PROCESSANDO);

    private final RabbitTemplate rabbit;
    private final MessageContract contract;
    private final SourceFileRepository files;
    private final TransactionTemplate transaction;
    private final Duration resendAfter;
    private final int maxAttempts;

    FilePublisher(RabbitTemplate rabbit, MessageContract contract, SourceFileRepository files,
            PlatformTransactionManager transactionManager, ApiProperties properties) {
        this.rabbit = rabbit;
        this.contract = contract;
        this.files = files;
        this.transaction = new TransactionTemplate(transactionManager);
        this.resendAfter = Duration.ofMinutes(properties.processing().resendAfterMinutes());
        this.maxAttempts = properties.processing().maxAttempts();
    }

    @TransactionalEventListener
    void afterCommit(FileReadRequested event) {
        files.findById(event.fileId()).ifPresent(this::send);
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    void periodically() {
        sweep(Instant.now().minus(resendAfter));
    }

    /** Resends what stalled before the cutoff; after too many attempts, marks it as Failed with the reason. */
    void sweep(Instant cutoff) {
        List<SourceFile> stalled = transaction.execute(t -> {
            List<SourceFile> list = files.findByStatusInAndQueuedAtBeforeOrderByUploadedAt(IN_PROGRESS, cutoff);
            List<SourceFile> toResend = list.stream().filter(a -> a.getAttempts() < maxAttempts).toList();
            list.stream().filter(a -> a.getAttempts() >= maxAttempts).forEach(a -> a.fail(
                    "A leitura não terminou depois de " + a.getAttempts() + " tentativas. O serviço rag está rodando?"));
            toResend.forEach(SourceFile::resend);
            return toResend;
        });
        if (!stalled.isEmpty()) {
            log.info("Reenviando {} arquivo(s) parado(s) para leitura", stalled.size());
            stalled.forEach(this::send);
        }
    }

    private void send(SourceFile file) {
        var properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding("UTF-8");
        try {
            rabbit.send("", QueueConfig.FILES_RECEIVED,
                    new Message(contract.write(FileReceivedMessage.from(file)), properties));
        } catch (AmqpException error) {
            // The file stays Pending; the sweep tries again
            log.warn("Fila indisponível ao enviar {}: {}", file.getOriginalName(), error.getMessage());
        }
    }
}
