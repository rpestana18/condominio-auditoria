package br.com.condominioauditoria.api.grpc;

import br.com.condominioauditoria.api.config.properties.ApiProperties;
import br.com.condominioauditoria.contratos.assistente.v1.AssistenteGrpc;
import br.com.condominioauditoria.contratos.assistente.v1.BuscarRequest;
import br.com.condominioauditoria.contratos.assistente.v1.BuscarResponse;
import br.com.condominioauditoria.contratos.assistente.v1.ListarProvedoresRequest;
import br.com.condominioauditoria.contratos.assistente.v1.ListarProvedoresResponse;
import br.com.condominioauditoria.contratos.assistente.v1.PerguntarEvento;
import br.com.condominioauditoria.contratos.assistente.v1.PerguntarRequest;
import br.com.condominioauditoria.contratos.assistente.v1.RespostaPergunta;
import io.grpc.Channel;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.stub.MetadataUtils;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Cliente do serviço Assistente do rag (contracts/grpc/assistente/v1). Repassa o token do usuário no metadado
 * "authorization", como o mcp faz com o backend, e toda chamada tem prazo (deadline) configurável.
 *
 * Perguntar é síncrono do ponto de vista de quem chama: a thread da API REST espera o fluxo inteiro. Enquanto isso o
 * rag chama de volta o servidor gRPC do backend (Consulta, com o mesmo token), que roda em outro grupo de threads
 * (ver {@link ServidorGrpc}); por isso a ida e volta não trava.
 */
@Component
public class ClienteAssistente {

    private final Channel canal;
    private final long prazoSegundos;
    private final long prazoPerguntaSegundos;

    public ClienteAssistente(Channel canalRag, ApiProperties propriedades) {
        this.canal = canalRag;
        this.prazoSegundos = propriedades.rag().timeoutSeconds();
        this.prazoPerguntaSegundos = propriedades.rag().questionTimeoutSeconds();
    }

    public BuscarResponse buscar(BuscarRequest pedido, String autorizacao) {
        return stub(autorizacao, prazoSegundos).buscar(pedido);
    }

    public ListarProvedoresResponse listarProvedores(String autorizacao) {
        return stub(autorizacao, prazoSegundos).listarProvedores(ListarProvedoresRequest.getDefaultInstance());
    }

    /**
     * Junta o fluxo de Perguntar: ignora os eventos de andamento e devolve o último evento "resposta". Erro do rag
     * (status gRPC) sobe como {@link io.grpc.StatusRuntimeException}; fluxo que termina sem resposta vira INTERNAL.
     */
    public RespostaPergunta perguntar(PerguntarRequest pedido, String autorizacao) {
        Iterator<PerguntarEvento> eventos = stub(autorizacao, prazoPerguntaSegundos).perguntar(pedido);
        RespostaPergunta resposta = null;
        while (eventos.hasNext()) {
            PerguntarEvento evento = eventos.next();
            if (evento.hasResposta()) {
                resposta = evento.getResposta();
            }
        }
        if (resposta == null) {
            throw Status.INTERNAL.withDescription("O rag terminou a pergunta sem mandar a resposta").asRuntimeException();
        }
        return resposta;
    }

    public long prazoPerguntaSegundos() {
        return prazoPerguntaSegundos;
    }

    private AssistenteGrpc.AssistenteBlockingStub stub(String autorizacao, long prazo) {
        var cabecalhos = new Metadata();
        cabecalhos.put(AutenticacaoGrpc.AUTORIZACAO, autorizacao);
        return AssistenteGrpc.newBlockingStub(canal)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(cabecalhos))
                .withDeadlineAfter(prazo, TimeUnit.SECONDS);
    }
}
