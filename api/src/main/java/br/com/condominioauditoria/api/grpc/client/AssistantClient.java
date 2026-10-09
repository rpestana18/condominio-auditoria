package br.com.condominioauditoria.api.grpc.client;

import br.com.condominioauditoria.api.config.properties.ApiProperties;
import br.com.condominioauditoria.api.grpc.server.GrpcAuthInterceptor;
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
 * Client of the rag's Assistant service (contracts/grpc/assistente/v1). Forwards the user's token in the
 * "authorization" metadata, as the mcp does with the api, and every call has a configurable deadline.
 *
 * Asking is synchronous from the caller's point of view: the REST API thread waits for the whole stream. Meanwhile the
 * rag calls back the api's gRPC server (Consulta, with the same token), which runs on another thread pool (see {@link
 * GrpcServer}); that is why the round trip does not deadlock.
 */
@Component
public class AssistantClient {

    private final Channel channel;
    private final long timeoutSeconds;
    private final long questionTimeoutSeconds;

    public AssistantClient(Channel ragChannel, ApiProperties properties) {
        this.channel = ragChannel;
        this.timeoutSeconds = properties.rag().timeoutSeconds();
        this.questionTimeoutSeconds = properties.rag().questionTimeoutSeconds();
    }

    public BuscarResponse search(BuscarRequest request, String authorization) {
        return stub(authorization, timeoutSeconds).buscar(request);
    }

    public ListarProvedoresResponse listProviders(String authorization) {
        return stub(authorization, timeoutSeconds).listarProvedores(ListarProvedoresRequest.getDefaultInstance());
    }

    /**
     * Collects the Perguntar stream: ignores the progress events and returns the last "resposta" event. A rag error
     * (gRPC status) propagates as {@link io.grpc.StatusRuntimeException}; a stream that ends without an answer becomes
     * INTERNAL.
     */
    public RespostaPergunta ask(PerguntarRequest request, String authorization) {
        Iterator<PerguntarEvento> events = stub(authorization, questionTimeoutSeconds).perguntar(request);
        RespostaPergunta response = null;
        while (events.hasNext()) {
            PerguntarEvento event = events.next();
            if (event.hasResposta()) {
                response = event.getResposta();
            }
        }
        if (response == null) {
            throw Status.INTERNAL.withDescription("O rag terminou a pergunta sem mandar a resposta").asRuntimeException();
        }
        return response;
    }

    public long questionTimeoutSeconds() {
        return questionTimeoutSeconds;
    }

    private AssistenteGrpc.AssistenteBlockingStub stub(String authorization, long timeout) {
        var headers = new Metadata();
        headers.put(GrpcAuthInterceptor.AUTHORIZATION, authorization);
        return AssistenteGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
                .withDeadlineAfter(timeout, TimeUnit.SECONDS);
    }
}
