package br.com.condominioauditoria.backend.mensagens;

import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Filas entre backend e rag (RabbitMQ). As duas pontas declaram as mesmas filas, com os mesmos argumentos, para
 * que nenhuma mensagem se perca se uma delas subir primeiro. Mensagem que falha depois das retentativas vai para
 * a fila ".erro", onde fica para análise em vez de sumir.
 */
@Configuration
public class Filas {

    /** backend → rag: arquivo guardado, precisa ser lido. */
    public static final String ARQUIVOS_RECEBIDOS = "rag.arquivos-recebidos";

    /** rag → backend: início, dados extraídos ou falha. */
    public static final String RESULTADOS = "backend.resultados";

    /** backend → rag: indexar (ou retirar) um arquivo para a busca nos documentos (ADR 0003, Decisão 5.1). */
    public static final String INDEXACAO = "rag.indexacao";

    /** rag → backend: andamento e resultado da indexação. */
    public static final String RESULTADOS_INDEXACAO = "backend.indexacao";

    @Bean
    Declarables declaracaoDasFilas() {
        return new Declarables(
                fila(ARQUIVOS_RECEBIDOS), QueueBuilder.durable(ARQUIVOS_RECEBIDOS + ".erro").build(),
                fila(RESULTADOS), QueueBuilder.durable(RESULTADOS + ".erro").build(),
                fila(INDEXACAO), QueueBuilder.durable(INDEXACAO + ".erro").build(),
                fila(RESULTADOS_INDEXACAO), QueueBuilder.durable(RESULTADOS_INDEXACAO + ".erro").build());
    }

    private static Queue fila(String nome) {
        return QueueBuilder.durable(nome)
                .deadLetterExchange("")
                .deadLetterRoutingKey(nome + ".erro")
                .build();
    }
}
