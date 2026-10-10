package br.com.condominioauditoria.api.config;

import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Queues between the api and the rag (RabbitMQ). Both ends declare the same queues with the same arguments, so no
 * message is lost when one of them starts first. A message that still fails after the retries goes to the ".error"
 * queue, where it stays for analysis instead of disappearing.
 */
@Configuration
public class QueueConfig {

    /** api → rag: file stored, it needs to be read. */
    public static final String FILES_RECEIVED = "rag.files-received";

    /** rag → api: start, extracted data or failure. */
    public static final String PROCESSING_RESULTS = "api.processing-results";

    /** api → rag: index (or withdraw) a file for the document search (ADR 0003, Decision 5.1). */
    public static final String INDEXING = "rag.indexing";

    /** rag → api: indexing progress and result. */
    public static final String INDEXING_RESULTS = "api.indexing-results";

    @Bean
    Declarables queues() {
        return new Declarables(
                queue(FILES_RECEIVED), QueueBuilder.durable(FILES_RECEIVED + ".error").build(),
                queue(PROCESSING_RESULTS), QueueBuilder.durable(PROCESSING_RESULTS + ".error").build(),
                queue(INDEXING), QueueBuilder.durable(INDEXING + ".error").build(),
                queue(INDEXING_RESULTS), QueueBuilder.durable(INDEXING_RESULTS + ".error").build());
    }

    private static Queue queue(String name) {
        return QueueBuilder.durable(name)
                .deadLetterExchange("")
                .deadLetterRoutingKey(name + ".error")
                .build();
    }
}
