package br.com.condominioauditoria.backend.mensagens;

import br.com.condominioauditoria.backend.arquivo.Arquivo;
import br.com.condominioauditoria.backend.arquivo.ArquivoRepository;
import br.com.condominioauditoria.backend.arquivo.StatusArquivo;
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
 * Pede ao rag a leitura de um arquivo, pela fila. A mensagem só sai depois do commit do registro do arquivo.
 *
 * As filas são duráveis: se o rag ou o backend reiniciarem, as mensagens esperam por eles. E nada fica parado para
 * sempre: se o RabbitMQ estiver fora do ar no envio, ou se uma leitura sumir no caminho, a varredura (a cada minuto)
 * reenvia o que está Pendente ou Processando há mais tempo que o limite. Reenviar é seguro: o backend
 * só aplica um resultado por processamentoId e a gravação apaga a extração anterior antes de inserir.
 */
@Component
public class PublicadorArquivos {

    private static final Logger log = LoggerFactory.getLogger(PublicadorArquivos.class);
    private static final List<StatusArquivo> EM_ANDAMENTO = List.of(StatusArquivo.PENDENTE, StatusArquivo.PROCESSANDO);

    private final RabbitTemplate rabbit;
    private final ContratoMensagens contrato;
    private final ArquivoRepository arquivos;
    private final TransactionTemplate transacao;
    private final Duration reenviarApos;
    private final int maxTentativas;

    PublicadorArquivos(RabbitTemplate rabbit, ContratoMensagens contrato, ArquivoRepository arquivos,
            PlatformTransactionManager transacoes, PropriedadesCondominio propriedades) {
        this.rabbit = rabbit;
        this.contrato = contrato;
        this.arquivos = arquivos;
        this.transacao = new TransactionTemplate(transacoes);
        this.reenviarApos = Duration.ofMinutes(propriedades.processamento().reenviarAposMinutos());
        this.maxTentativas = propriedades.processamento().maxTentativas();
    }

    /** Publicado por quem grava ou reprocessa o arquivo; só dispara depois do commit. */
    public record ArquivoParaLer(UUID arquivoId) {
    }

    @TransactionalEventListener
    void aposCommit(ArquivoParaLer evento) {
        arquivos.findById(evento.arquivoId()).ifPresent(this::enviar);
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    void periodicamente() {
        varrer(Instant.now().minus(reenviarApos));
    }

    /** Reenvia o que ficou parado antes do limite; depois de muitas tentativas, marca como Falhou com o motivo. */
    void varrer(Instant limite) {
        List<Arquivo> parados = transacao.execute(t -> {
            List<Arquivo> lista = arquivos.findByStatusInAndEnfileiradoEmBeforeOrderByEnviadoEm(EM_ANDAMENTO, limite);
            List<Arquivo> reenviar = lista.stream().filter(a -> a.getTentativas() < maxTentativas).toList();
            lista.stream().filter(a -> a.getTentativas() >= maxTentativas).forEach(a -> a.falhar(
                    "A leitura não terminou depois de " + a.getTentativas() + " tentativas. O serviço rag está rodando?"));
            reenviar.forEach(Arquivo::reenviar);
            return reenviar;
        });
        if (!parados.isEmpty()) {
            log.info("Reenviando {} arquivo(s) parado(s) para leitura", parados.size());
            parados.forEach(this::enviar);
        }
    }

    private void enviar(Arquivo arquivo) {
        var propriedades = new MessageProperties();
        propriedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        propriedades.setContentEncoding("UTF-8");
        try {
            rabbit.send("", Filas.ARQUIVOS_RECEBIDOS,
                    new Message(contrato.escrever(ArquivoRecebido.de(arquivo)), propriedades));
        } catch (AmqpException erro) {
            // O arquivo continua Pendente; a varredura tenta de novo
            log.warn("Fila indisponível ao enviar {}: {}", arquivo.getNomeOriginal(), erro.getMessage());
        }
    }
}
