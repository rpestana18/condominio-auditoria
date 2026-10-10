package br.com.condominioauditoria.api.grpc.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.api.config.properties.ApiProperties;
import br.com.condominioauditoria.api.grpc.client.AssistantClient;
import br.com.condominioauditoria.api.model.condominium.Condominium;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.query.QueryService;
import br.com.condominioauditoria.contracts.assistant.v2.AssistantGrpc;
import br.com.condominioauditoria.contracts.assistant.v2.StoredData;
import br.com.condominioauditoria.contracts.assistant.v2.DataRow;
import br.com.condominioauditoria.contracts.assistant.v2.AskEvent;
import br.com.condominioauditoria.contracts.assistant.v2.AskRequest;
import br.com.condominioauditoria.contracts.assistant.v2.Answer;
import br.com.condominioauditoria.contracts.assistant.v2.AnswerOutcome;
import br.com.condominioauditoria.contracts.query.v2.QueryGrpc;
import br.com.condominioauditoria.contracts.query.v2.ListCondominiumsRequest;
import br.com.condominioauditoria.contracts.query.v2.ListCondominiumsResponse;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/**
 * Question round trip (ADR 0003, Decision 5.2): an "API" thread calls the rag (Ask) and waits; during the
 * question, the rag calls the api's Consulta through the real gRPC server (real port, its own thread pool) with the
 * user's token received in the metadata. Checks that the api accepts the token forwarded by the rag, applies the user's
 * access (only condominium A) and that nothing deadlocks with a single API thread.
 */
class QuestionRoundTripTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    private GrpcServer api;
    private Server rag;
    private ManagedChannel ragChannel;
    private ManagedChannel ragToApiChannel;
    private final AtomicReference<String> queryThread = new AtomicReference<>();

    private final JwtDecoder decoder = token -> switch (token) {
        case "usuario-a" -> new Jwt(token, Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "none"),
                Map.of("preferred_username", "usuario.a", "perfis", List.of("USUARIO"), "condominios",
                        List.of(A.toString())));
        default -> throw new BadJwtException("assinatura inválida");
    };

    @BeforeEach
    void setUp() throws Exception {
        CondominiumRepository condominiums = mock(CondominiumRepository.class);
        Condominium ca = mock(Condominium.class);
        when(ca.getId()).thenReturn(A);
        when(ca.getName()).thenReturn("Condomínio A");
        Condominium cb = mock(Condominium.class);
        when(cb.getId()).thenReturn(B);
        when(cb.getName()).thenReturn("Condomínio B");
        when(condominiums.findAll()).thenAnswer(i -> {
            queryThread.set(Thread.currentThread().getName());
            return List.of(ca, cb);
        });
        var access = new CondominiumAccess();
        var query = new QueryGrpcService(new QueryService(access, condominiums, null, null, null, null, null, null));
        var converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("preferred_username");
        converter.setJwtGrantedAuthoritiesConverter(jwt -> jwt.getClaimAsStringList("perfis").stream()
                .map(p -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + p)).toList());
        api = new GrpcServer(query, new GrpcAuthInterceptor(decoder, converter),
                new ApiProperties(null, null, new ApiProperties.GrpcProperties(0), null));
        api.start();
        ragToApiChannel = Grpc.newChannelBuilder("localhost:" + api.port(), InsecureChannelCredentials.create())
                .build();

        // fake rag: in the middle of the question, calls the api's Consulta with the token it received
        var receivedToken = new AtomicReference<String>();
        var assistant = new AssistantGrpc.AssistantImplBase() {
            @Override
            public void ask(AskRequest request, StreamObserver<AskEvent> response) {
                var headers = new Metadata();
                headers.put(GrpcAuthInterceptor.AUTHORIZATION, receivedToken.get());
                ListCondominiumsResponse list = QueryGrpc.newBlockingStub(ragToApiChannel)
                        .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
                        .withDeadlineAfter(10, TimeUnit.SECONDS)
                        .listCondominiums(ListCondominiumsRequest.getDefaultInstance());
                var data = StoredData.newBuilder().setCallId("c1").setQuery("listar_condominios");
                list.getCondominiumsList().forEach(c -> data.addRows(DataRow.newBuilder().setLabel("Condomínio")
                        .setValue(c.getName())));
                response.onNext(AskEvent.newBuilder().setAnswer(Answer.newBuilder()
                        .setOutcome(AnswerOutcome.ANSWER_OUTCOME_ANSWERED).addInStoredData(data)).build());
                response.onCompleted();
            }
        };
        var tokenCapture = new ServerInterceptor() {
            @Override
            public <Q, R> ServerCall.Listener<Q> interceptCall(ServerCall<Q, R> call, Metadata headers,
                    ServerCallHandler<Q, R> next) {
                receivedToken.set(headers.get(GrpcAuthInterceptor.AUTHORIZATION));
                return next.startCall(call, headers);
            }
        };
        String name = InProcessServerBuilder.generateName();
        rag = InProcessServerBuilder.forName(name).addService(ServerInterceptors.intercept(assistant, tokenCapture))
                .build().start();
        ragChannel = InProcessChannelBuilder.forName(name).build();
    }

    @AfterEach
    void tearDown() {
        ragChannel.shutdownNow();
        rag.shutdownNow();
        ragToApiChannel.shutdownNow();
        api.stop();
    }

    @Test
    void ragCallsQueryBackWithUserTokenWithoutBlockingApiThread() throws Exception {
        var client = new AssistantClient(ragChannel, new ApiProperties(null, null, null,
                new ApiProperties.RagProperties("rag:9091", 5, 20, 300)));
        ExecutorService apiThread = Executors.newSingleThreadExecutor(r -> new Thread(r, "http-nio-teste"));
        try {
            Answer response = apiThread.submit(() -> client.ask(AskRequest.newBuilder()
                    .setCondominiumId(A.toString()).setQuestion("quais condomínios?").build(), "Bearer usuario-a"))
                    .get(20, TimeUnit.SECONDS);

            assertThat(response.getInStoredData(0).getRowsList()).extracting(DataRow::getValue)
                    .containsExactly("Condomínio A"); // B does not show up: the user's access applied
            assertThat(queryThread.get()).startsWith("grpc-servidor-");
        } finally {
            apiThread.shutdownNow();
        }
    }
}
