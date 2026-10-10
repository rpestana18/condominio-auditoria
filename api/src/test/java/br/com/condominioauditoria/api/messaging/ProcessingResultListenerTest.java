package br.com.condominioauditoria.api.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import br.com.condominioauditoria.api.service.file.FileStatusService;
import br.com.condominioauditoria.api.service.file.ProcessingResultService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

/**
 * ADR 0006, phase 2 (v2 cut): a v2 result is rejected without touching the database. The exception makes the queue
 * container reject the message without requeueing it (default-requeue-rejected: false), and RabbitMQ sends it to
 * api.processing-results.error (dead letter declared in QueueConfig).
 */
class ProcessingResultListenerTest {

    private final FileStatusService fileStatus = mock(FileStatusService.class);
    private final ProcessingResultService processingResults = mock(ProcessingResultService.class);
    private final ProcessingResultListener listener = new ProcessingResultListener(new MessageContract(), fileStatus, processingResults);

    @Test
    void v2ResultIsRejectedAndSavesNothing() throws Exception {
        var message = new Message(MessageContractTest.example("v2", "resultado-concluido-fluxo.json"),
                new MessageProperties());

        assertThatThrownBy(() -> listener.onMessage(message)).hasMessageContaining("fora do contrato");
        verifyNoInteractions(fileStatus, processingResults);
    }

    @Test
    void v3CashFlowResultGoesToTheWriter() throws Exception {
        var message = new Message(MessageContractTest.example("v3", "processing-result-cash-flow.json"),
                new MessageProperties());

        listener.onMessage(message);

        verify(processingResults).save(org.mockito.ArgumentMatchers.argThat(r -> r.version() == 3));
    }

    @Test
    void v3BudgetResultIsAccepted() throws Exception {
        var message = new Message(MessageContractTest.example("v3", "processing-result-budget.json"),
                new MessageProperties());

        listener.onMessage(message);

        verify(processingResults).save(org.mockito.ArgumentMatchers.argThat(r -> r.budget() != null));
    }
}
