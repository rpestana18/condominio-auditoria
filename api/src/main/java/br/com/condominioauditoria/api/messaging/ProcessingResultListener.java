package br.com.condominioauditoria.api.messaging;

import br.com.condominioauditoria.api.config.QueueConfig;
import br.com.condominioauditoria.api.service.file.FileStatusService;
import br.com.condominioauditoria.api.service.file.ProcessingResultService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Receives the reading progress and results from the rag. A single consumer, so that "started" and "completed" of the
 * same file are applied in the order they arrived. If saving fails, the message goes back to the queue (retry) and, if
 * it keeps failing, goes to backend.resultados.erro.
 */
@Component
class ProcessingResultListener {

    private final MessageContract contract;
    private final FileStatusService fileStatus;
    private final ProcessingResultService processingResults;

    ProcessingResultListener(MessageContract contract, FileStatusService fileStatus,
            ProcessingResultService processingResults) {
        this.contract = contract;
        this.fileStatus = fileStatus;
        this.processingResults = processingResults;
    }

    @RabbitListener(queues = QueueConfig.PROCESSING_RESULTS, concurrency = "1")
    void onMessage(Message message) {
        ProcessingResultMessage result = contract.readProcessingResult(message.getBody());
        switch (result.status()) {
            case INICIADO -> fileStatus.processing(result.fileId(), result.processingId());
            case FALHOU -> fileStatus.failed(result.fileId(), result.processingId(), result.reason());
            case CONCLUIDO -> processingResults.save(result);
        }
    }
}
