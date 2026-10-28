package br.com.condominioauditoria.api.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import br.com.condominioauditoria.logging.LogContext;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * ADR 0008: the request id goes in the AMQP header "requestId" (the body does not change), and a message that gives up
 * after the retries leaves one ERROR line with its ids, never its body.
 */
@ExtendWith(OutputCaptureExtension.class)
class RequestIdQueueTest {

    private static final String BODY = """
            {"contractVersion":"3","fileId":"11111111-2222-3333-4444-555555555555",
             "processingId":"66666666-7777-8888-9999-000000000000","originalName":"Recibo Fulano 123.456.789-00.pdf",
             "status":"COMPLETED"}""";

    @Test
    void publishedMessagesCarryTheRequestIdOfTheRequest() throws Exception {
        RabbitTemplate template = new RabbitTemplate(mock(ConnectionFactory.class));
        RabbitTemplateCustomizer customizer = new br.com.condominioauditoria.api.config.QueueConfig().requestIdHeader();
        customizer.customize(template);
        MessagePostProcessor processor = template.getBeforePublishPostProcessors().iterator().next();

        Message sent;
        try (LogContext.Scope ignored = LogContext.with(LogContext.REQUEST_ID, "req-fila")) {
            sent = processor.postProcessMessage(new Message(BODY.getBytes(StandardCharsets.UTF_8)));
        }
        assertThat((String) sent.getMessageProperties().getHeader(LogContext.AMQP_HEADER)).isEqualTo("req-fila");
        assertThat(new String(sent.getBody(), StandardCharsets.UTF_8)).isEqualTo(BODY);

        // Outside a request (the scan that resends stalled files): a new id
        Message scan = processor.postProcessMessage(new Message(BODY.getBytes(StandardCharsets.UTF_8)));
        assertThat(LogContext.isValid(scan.getMessageProperties().getHeader(LogContext.AMQP_HEADER))).isTrue();
    }

    @Test
    void givingUpLogsTheIdsAndTheCauseButNotTheBody(CapturedOutput output) {
        MessageProperties properties = new MessageProperties();
        properties.setConsumerQueue("api.processing-results");
        properties.setHeader(LogContext.AMQP_HEADER, "req-desistida");
        Message message = new Message(BODY.getBytes(StandardCharsets.UTF_8), properties);

        assertThatThrownBy(() -> new GiveUpRecoverer().recover(message, new IllegalStateException("gravação falhou")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        assertThat(output.getOut()).contains("ERROR").contains("api.processing-results")
                .contains("gravação falhou").doesNotContain("Recibo").doesNotContain("123.456.789-00");
        assertThat(br.com.condominioauditoria.logging.QueueLogContext.ids(message.getBody()))
                .containsEntry(LogContext.FILE_ID, "11111111-2222-3333-4444-555555555555")
                .containsEntry(LogContext.PROCESSING_ID, "66666666-7777-8888-9999-000000000000");
    }
}
