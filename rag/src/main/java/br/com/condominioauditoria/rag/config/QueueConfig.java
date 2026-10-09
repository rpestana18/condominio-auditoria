package br.com.condominioauditoria.rag.config;

import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Queues between the api and the rag (RabbitMQ). Both ends declare the same queues with the same arguments, so no
 * message is lost when one of them starts first. A message that still fails after the retries goes to the ".erro"
 * queue, where it stays for analysis instead of disappearing.
 */
@Configuration
public class QueueConfig {

    /** api → rag: file stored, it needs to be read. */
    public static final String FILES_RECEIVED = "rag.arquivos-recebidos";

    /** rag → api: start, extracted data or failure. */
    public static final String PROCESSING_RESULTS = "backend.resultados";

    /** api → rag: index or withdraw a file from the assistant's index (ADR 0003, Decision 5.1). */
    public static final String INDEXING = "rag.indexacao";

    /** rag → api: indexing progress and result. */
    public static final String INDEXING_RESULTS = "backend.indexacao";

    @Bean
    public Declarables queues() {
        return new Declarables(
                queue(FILES_RECEIVED), QueueBuilder.durable(FILES_RECEIVED + ".erro").build(),
                queue(PROCESSING_RESULTS), QueueBuilder.durable(PROCESSING_RESULTS + ".erro").build(),
                queue(INDEXING), QueueBuilder.durable(INDEXING + ".erro").build(),
                queue(INDEXING_RESULTS), QueueBuilder.durable(INDEXING_RESULTS + ".erro").build());
    }

    private static Queue queue(String name) {
        return QueueBuilder.durable(name)
                .deadLetterExchange("")
                .deadLetterRoutingKey(name + ".erro")
                .build();
    }
}
