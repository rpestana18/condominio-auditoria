package br.com.condominioauditoria.api.messaging;

import br.com.condominioauditoria.api.config.QueueConfig;
import br.com.condominioauditoria.api.service.file.IndexingResultService;
import br.com.condominioauditoria.logging.LogContext;
import br.com.condominioauditoria.logging.QueueLogContext;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Receives the indexing progress and results from the rag. A single consumer, so that "indexing" and "indexed" of the
 * same file are applied in the order they arrived. A message outside the contract or a failing save goes back to the
 * queue (retry) and, if it keeps failing, goes to api.indexing-results.error.
 */
@Component
class IndexingResultListener {

    private final MessageContract contract;
    private final IndexingResultService indexingResults;

    IndexingResultListener(MessageContract contract, IndexingResultService indexing) {
        this.contract = contract;
        this.indexingResults = indexing;
    }

    @RabbitListener(queues = QueueConfig.INDEXING_RESULTS, concurrency = "1")
    void onMessage(Message message) {
        try (LogContext.Scope ignored = QueueLogContext.enter(message.getBody(),
                message.getMessageProperties().getHeader(LogContext.AMQP_HEADER))) {
            indexingResults.apply(contract.readIndexingResult(message.getBody()));
        }
    }
}
