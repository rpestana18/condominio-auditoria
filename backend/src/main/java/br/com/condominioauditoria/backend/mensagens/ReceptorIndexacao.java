package br.com.condominioauditoria.backend.mensagens;

import br.com.condominioauditoria.backend.processamento.IndexacaoArquivoService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Recebe do rag o andamento e o resultado das indexações. Um consumidor só, para que "indexando" e "indexado" de um
 * mesmo arquivo sejam aplicados na ordem em que chegaram. Mensagem fora do contrato ou gravação que falha volta para
 * a fila (retentativa) e, se continuar falhando, vai para backend.indexacao.erro.
 */
@Component
class ReceptorIndexacao {

    private final ContratoMensagens contrato;
    private final IndexacaoArquivoService indexacao;

    ReceptorIndexacao(ContratoMensagens contrato, IndexacaoArquivoService indexacao) {
        this.contrato = contrato;
        this.indexacao = indexacao;
    }

    @RabbitListener(queues = Filas.RESULTADOS_INDEXACAO, concurrency = "1")
    void aoReceber(Message mensagem) {
        indexacao.aplicar(contrato.lerResultadoIndexacao(mensagem.getBody()));
    }
}
