package br.com.condominioauditoria.api.messaging;

import br.com.condominioauditoria.api.config.QueueConfig;
import br.com.condominioauditoria.api.service.file.IndexingResultService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Receives the indexing progress and results from the rag. A single consumer, so that "indexing" and "indexed" of the
 * same file are applied in the order they arrived. A message outside the contract or a failing save goes back to the
 * queue (retry) and, if it keeps failing, goes to backend.indexacao.erro.
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
        indexingResults.apply(contract.readIndexingResult(message.getBody()));
    }
}
