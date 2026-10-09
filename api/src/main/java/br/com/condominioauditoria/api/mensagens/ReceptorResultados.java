package br.com.condominioauditoria.api.mensagens;

import br.com.condominioauditoria.api.processamento.GravacaoResultado;
import br.com.condominioauditoria.api.processamento.StatusArquivoService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Recebe do rag o andamento e o resultado das leituras. Um consumidor só, para que "iniciado" e "concluído" de um
 * mesmo arquivo sejam aplicados na ordem em que chegaram. Se a gravação falhar, a mensagem volta para a fila
 * (retentativa) e, se continuar falhando, vai para backend.resultados.erro.
 */
@Component
class ReceptorResultados {

    private final ContratoMensagens contrato;
    private final StatusArquivoService status;
    private final GravacaoResultado gravacao;

    ReceptorResultados(ContratoMensagens contrato, StatusArquivoService status, GravacaoResultado gravacao) {
        this.contrato = contrato;
        this.status = status;
        this.gravacao = gravacao;
    }

    @RabbitListener(queues = Filas.RESULTADOS, concurrency = "1")
    void aoReceber(Message mensagem) {
        ResultadoProcessamento resultado = contrato.lerResultado(mensagem.getBody());
        switch (resultado.situacao()) {
            case INICIADO -> status.processando(resultado.arquivoId(), resultado.processamentoId());
            case FALHOU -> status.falhou(resultado.arquivoId(), resultado.processamentoId(), resultado.motivo());
            case CONCLUIDO -> gravacao.gravar(resultado);
        }
    }
}
