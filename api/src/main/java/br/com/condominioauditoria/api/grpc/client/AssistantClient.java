package br.com.condominioauditoria.api.grpc.client;

import br.com.condominioauditoria.api.config.properties.ApiProperties;
import br.com.condominioauditoria.api.grpc.server.GrpcAuthInterceptor;
import br.com.condominioauditoria.contracts.assistant.v2.AssistantGrpc;
import br.com.condominioauditoria.contracts.assistant.v2.SearchRequest;
import br.com.condominioauditoria.contracts.assistant.v2.SearchResponse;
import br.com.condominioauditoria.contracts.assistant.v2.ListProvidersRequest;
import br.com.condominioauditoria.contracts.assistant.v2.ListProvidersResponse;
import br.com.condominioauditoria.contracts.assistant.v2.AskEvent;
import br.com.condominioauditoria.contracts.assistant.v2.AskRequest;
import br.com.condominioauditoria.contracts.assistant.v2.Answer;
import io.grpc.Channel;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.stub.MetadataUtils;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Client of the rag's Assistant service (contracts/grpc/assistant/v2). Forwards the user's token in the
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

    public SearchResponse search(SearchRequest request, String authorization) {
        return stub(authorization, timeoutSeconds).search(request);
    }

    public ListProvidersResponse listProviders(String authorization) {
        return stub(authorization, timeoutSeconds).listProviders(ListProvidersRequest.getDefaultInstance());
    }

    /**
     * Collects the Ask stream: ignores the progress events and returns the last "resposta" event. A rag error
     * (gRPC status) propagates as {@link io.grpc.StatusRuntimeException}; a stream that ends without an answer becomes
     * INTERNAL.
     */
    public Answer ask(AskRequest request, String authorization) {
        Iterator<AskEvent> events = stub(authorization, questionTimeoutSeconds).ask(request);
        Answer response = null;
        while (events.hasNext()) {
            AskEvent event = events.next();
            if (event.hasAnswer()) {
                response = event.getAnswer();
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

    private AssistantGrpc.AssistantBlockingStub stub(String authorization, long timeout) {
        var headers = new Metadata();
        headers.put(GrpcAuthInterceptor.AUTHORIZATION, authorization);
        return AssistantGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
                .withDeadlineAfter(timeout, TimeUnit.SECONDS);
    }
}
