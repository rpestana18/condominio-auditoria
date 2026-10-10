package br.com.condominioauditoria.api.grpc.client;

import br.com.condominioauditoria.api.config.properties.ApiProperties;
import br.com.condominioauditoria.contracts.assistant.v2.AssistantGrpc;
import br.com.condominioauditoria.contracts.assistant.v2.SearchRequest;
import br.com.condominioauditoria.contracts.assistant.v2.SearchResponse;
import br.com.condominioauditoria.contracts.assistant.v2.AskEvent;
import br.com.condominioauditoria.contracts.assistant.v2.AskRequest;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/** In-process fake rag: keeps what it received and answers as the test says. */
public class FakeRag implements AutoCloseable {

    public static final Metadata.Key<String> AUTHORIZATION = Metadata.Key.of("authorization",
            Metadata.ASCII_STRING_MARSHALLER);

    public final List<AskRequest> questions = new CopyOnWriteArrayList<>();
    public final List<SearchRequest> searches = new CopyOnWriteArrayList<>();
    public final AtomicReference<String> token = new AtomicReference<>();
    public volatile BiConsumer<AskRequest, StreamObserver<AskEvent>> onAsk = (p, r) -> r.onCompleted();
    public volatile BiConsumer<SearchRequest, StreamObserver<SearchResponse>> onSearch = (p, r) -> {
        r.onNext(SearchResponse.getDefaultInstance());
        r.onCompleted();
    };

    private final Server server;
    private final ManagedChannel channel;
    public final AssistantClient client;

    public FakeRag() throws Exception {
        var service = new AssistantGrpc.AssistantImplBase() {
            @Override
            public void ask(AskRequest request, StreamObserver<AskEvent> response) {
                questions.add(request);
                onAsk.accept(request, response);
            }

            @Override
            public void search(SearchRequest request, StreamObserver<SearchResponse> response) {
                searches.add(request);
                onSearch.accept(request, response);
            }
        };
        var tokenCapture = new ServerInterceptor() {
            @Override
            public <Q, R> ServerCall.Listener<Q> interceptCall(ServerCall<Q, R> call, Metadata headers,
                    ServerCallHandler<Q, R> next) {
                token.set(headers.get(AUTHORIZATION));
                return next.startCall(call, headers);
            }
        };
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).addService(ServerInterceptors.intercept(service, tokenCapture))
                .build().start();
        channel = InProcessChannelBuilder.forName(name).build();
        client = new AssistantClient(channel, new ApiProperties(null, null, null,
                new ApiProperties.RagProperties("rag:9091", 5, 7, 300)));
    }

    public void shutDown() {
        server.shutdownNow();
    }

    @Override
    public void close() {
        channel.shutdownNow();
        server.shutdownNow();
    }
}
