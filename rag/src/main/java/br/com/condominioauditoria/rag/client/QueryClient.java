package br.com.condominioauditoria.rag.client;

import br.com.condominioauditoria.contracts.query.v2.QueryGrpc;
import br.com.condominioauditoria.rag.config.properties.RagProperties;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Client of the api's gRPC {@code contracts/grpc/query/v2}, used by the chat's numeric tools (ADR 0003,
 * Decision 5.2: "o rag chama as ferramentas no backend com o token do próprio usuário"). The same token that arrived
 * in the {@code authorization} metadata of {@code Ask} is passed on; the api applies role and condominium as it
 * does for the mcp.
 *
 * A single channel for the service (address {@code BACKEND_GRPC}); the deadline is per call, so the chat never hangs.
 */
@Component
public class QueryClient {

    private static final Logger log = LoggerFactory.getLogger(QueryClient.class);

    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final ManagedChannel channel;
    private final int timeoutSeconds;

    @Autowired
    QueryClient(RagProperties properties) {
        var config = properties.assistant();
        this.timeoutSeconds = config.toolTimeoutSeconds();
        this.channel = Grpc.newChannelBuilder(config.apiGrpc(), InsecureChannelCredentials.create()).build();
        log.info("Ferramentas numéricas do assistente vão para o gRPC Consulta em {}", config.apiGrpc());
    }

    /** For the tests: channel already built (in-process server). */
    public QueryClient(ManagedChannel channel, int timeoutSeconds) {
        this.channel = channel;
        this.timeoutSeconds = timeoutSeconds;
    }

    /** Stub with the token of the user of this question and the deadline of one call. */
    public QueryGrpc.QueryBlockingStub withToken(String authorization) {
        var headers = new Metadata();
        headers.put(AUTHORIZATION, authorization);
        return QueryGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
                .withDeadlineAfter(timeoutSeconds, TimeUnit.SECONDS);
    }

    @PreDestroy
    void close() {
        channel.shutdown();
        try {
            channel.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }
}
