package br.com.condominioauditoria.api.messaging;

import br.com.condominioauditoria.logging.LogContext;
import br.com.condominioauditoria.logging.QueueLogContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.stereotype.Component;

/**
 * What happens when a message still fails after the retries (ADR 0008, D7): one ERROR line with the queue, the ids of
 * the message ({@code fileId}, {@code processingId} or {@code indexingId}) and the cause with its stack, then the
 * message goes to the ".error" queue as before. The body is never logged (it carries names and amounts), so this
 * replaces Spring's default recoverer, whose WARN prints the message.
 */
@Component
class GiveUpRecoverer implements MessageRecoverer {

    private static final Logger log = LoggerFactory.getLogger(GiveUpRecoverer.class);

    @Override
    public void recover(Message message, Throwable cause) {
        try (LogContext.Scope ignored = QueueLogContext.enter(message.getBody(),
                message.getMessageProperties().getHeader(LogContext.AMQP_HEADER))) {
            log.error("Mensagem da fila {} desistida depois das tentativas; vai para {}.error", queue(message),
                    queue(message), cause);
        }
        // Same rejection as Spring's RejectAndDontRequeueRecoverer, without its WARN, which prints the body (B7)
        throw new AmqpRejectAndDontRequeueException("Mensagem desistida depois das tentativas", cause);
    }

    static String queue(Message message) {
        String queue = message.getMessageProperties().getConsumerQueue();
        return queue == null ? "(desconhecida)" : queue;
    }
}
