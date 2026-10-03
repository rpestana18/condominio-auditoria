package br.com.condominioauditoria.app.processamento;

import br.com.condominioauditoria.app.arquivo.Arquivo;
import br.com.condominioauditoria.app.arquivo.ArquivoRepository;
import br.com.condominioauditoria.dominio.StatusArquivo;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Entrada da fila. O processamento só começa depois do commit do registro do arquivo,
 * e na subida do sistema tudo o que ficou pela metade volta para a fila.
 */
@Component
public class FilaProcessamento {

    private static final Logger log = LoggerFactory.getLogger(FilaProcessamento.class);

    private final ProcessadorArquivo processador;
    private final ArquivoRepository arquivos;
    private final TransactionTemplate transacao;

    FilaProcessamento(ProcessadorArquivo processador, ArquivoRepository arquivos, PlatformTransactionManager transacoes) {
        this.processador = processador;
        this.arquivos = arquivos;
        this.transacao = new TransactionTemplate(transacoes);
    }

    /** Publicado por quem grava o arquivo; só dispara depois do commit. */
    public record ArquivoNaFila(UUID arquivoId) {
    }

    @TransactionalEventListener
    void aoReceber(ArquivoNaFila evento) {
        processador.processar(evento.arquivoId());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recuperarNaSubida() {
        List<UUID> pendentes = transacao.execute(t -> {
            List<Arquivo> lista = arquivos.findByStatusInOrderByEnviadoEm(
                    List.of(StatusArquivo.PENDENTE, StatusArquivo.PROCESSANDO));
            lista.forEach(Arquivo::voltarParaFila);
            return lista.stream().map(Arquivo::getId).toList();
        });
        if (!pendentes.isEmpty()) {
            log.info("Recolocando {} arquivo(s) na fila após reinício", pendentes.size());
            pendentes.forEach(processador::processar);
        }
    }
}
