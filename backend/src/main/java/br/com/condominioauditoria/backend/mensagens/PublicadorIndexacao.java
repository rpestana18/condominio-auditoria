package br.com.condominioauditoria.backend.mensagens;

import br.com.condominioauditoria.backend.arquivo.Arquivo;
import br.com.condominioauditoria.backend.arquivo.ArquivoRepository;
import br.com.condominioauditoria.backend.arquivo.SituacaoIndexacao;
import br.com.condominioauditoria.backend.config.PropriedadesCondominio;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
 * Pede ao rag a indexação de um arquivo para a busca nos documentos, pela fila rag.indexacao (ADR 0003, Decisão 5.1).
 * Mesmo padrão do {@link PublicadorArquivos}: a mensagem só sai depois do commit; a varredura (a cada minuto) reenvia
 * o que está Na fila ou Indexando há mais tempo que o limite e, depois de muitas tentativas, marca Erro com o motivo.
 * Reenviar é seguro: o backend só aplica resultado do indexacaoId atual, e o rag não refaz índice igual.
 */
@Component
public class PublicadorIndexacao {

    private static final Logger log = LoggerFactory.getLogger(PublicadorIndexacao.class);
    private static final List<SituacaoIndexacao> EM_ANDAMENTO = List.of(SituacaoIndexacao.NA_FILA,
            SituacaoIndexacao.INDEXANDO);

    private final RabbitTemplate rabbit;
    private final ContratoMensagens contrato;
    private final ArquivoRepository arquivos;
    private final TransactionTemplate transacao;
    private final Duration reenviarApos;
    private final int maxTentativas;

    PublicadorIndexacao(RabbitTemplate rabbit, ContratoMensagens contrato, ArquivoRepository arquivos,
            PlatformTransactionManager transacoes, PropriedadesCondominio propriedades) {
        this.rabbit = rabbit;
        this.contrato = contrato;
        this.arquivos = arquivos;
        this.transacao = new TransactionTemplate(transacoes);
        this.reenviarApos = Duration.ofMinutes(propriedades.processamento().reenviarAposMinutos());
        this.maxTentativas = propriedades.processamento().maxTentativas();
    }

    /** Publicado por quem grava ou reprocessa o arquivo, depois de {@code Arquivo.novaIndexacao()}; só dispara depois do commit. */
    public record ArquivoParaIndexar(UUID arquivoId) {
    }

    @TransactionalEventListener
    void aposCommit(ArquivoParaIndexar evento) {
        arquivos.findById(evento.arquivoId()).ifPresent(this::enviar);
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    void periodicamente() {
        varrer(Instant.now().minus(reenviarApos));
    }

    /** Reenvia o que ficou parado antes do limite; depois de muitas tentativas, marca Erro com o motivo. */
    void varrer(Instant limite) {
        List<Arquivo> parados = transacao.execute(t -> {
            List<Arquivo> lista = arquivos.findByIndexacaoSituacaoInAndIndexacaoEnfileiradaEmBeforeOrderByEnviadoEm(
                    EM_ANDAMENTO, limite);
            List<Arquivo> reenviar = lista.stream().filter(a -> a.getIndexacaoTentativas() < maxTentativas).toList();
            lista.stream().filter(a -> a.getIndexacaoTentativas() >= maxTentativas).forEach(a -> a.falharIndexacao(
                    "A indexação não terminou depois de " + a.getIndexacaoTentativas()
                            + " tentativas. O serviço rag está rodando?"));
            reenviar.forEach(Arquivo::reenviarIndexacao);
            return reenviar;
        });
        if (parados != null && !parados.isEmpty()) {
            log.info("Reenviando {} arquivo(s) parado(s) para indexação", parados.size());
            parados.forEach(this::enviar);
        }
    }

    private void enviar(Arquivo arquivo) {
        if (arquivo.getIndexacaoId() == null) {
            return;
        }
        var propriedades = new MessageProperties();
        propriedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        propriedades.setContentEncoding("UTF-8");
        try {
            rabbit.send("", Filas.INDEXACAO,
                    new Message(contrato.escrever(IndexarArquivo.indexar(arquivo)), propriedades));
        } catch (AmqpException erro) {
            // O pedido continua Na fila; a varredura tenta de novo
            log.warn("Fila indisponível ao pedir a indexação de {}: {}", arquivo.getNomeOriginal(), erro.getMessage());
        }
    }
}
