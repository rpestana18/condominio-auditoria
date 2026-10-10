package br.com.condominioauditoria.api.grpc.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.api.config.properties.ApiProperties;
import br.com.condominioauditoria.api.exception.FeatureNotEnabledException;
import br.com.condominioauditoria.api.grpc.client.AssistantClient;
import br.com.condominioauditoria.api.model.enums.AiMode;
import br.com.condominioauditoria.api.model.enums.FileCategory;
import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.repository.file.SourceFileRepository;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.ai.AiConfigurationService;
import br.com.condominioauditoria.api.service.feature.FeatureService;
import br.com.condominioauditoria.api.service.query.DocumentSearchService;
import br.com.condominioauditoria.api.service.query.QueryService;
import br.com.condominioauditoria.api.service.usage.UsageService;
import br.com.condominioauditoria.contracts.assistant.v2.AssistantGrpc;
import br.com.condominioauditoria.contracts.assistant.v2.SearchRequest;
import br.com.condominioauditoria.contracts.assistant.v2.SearchResponse;
import br.com.condominioauditoria.contracts.assistant.v2.PageLocation;
import br.com.condominioauditoria.contracts.assistant.v2.SheetLocation;
import br.com.condominioauditoria.contracts.assistant.v2.ChunkLocation;
import br.com.condominioauditoria.contracts.assistant.v2.SearchMode;
import br.com.condominioauditoria.contracts.assistant.v2.IndexedChunk;
import br.com.condominioauditoria.contracts.query.v2.SearchDocumentsRequest;
import br.com.condominioauditoria.contracts.query.v2.SearchDocumentsResponse;
import br.com.condominioauditoria.contracts.query.v2.QueryGrpc;
import br.com.condominioauditoria.contracts.query.v2.DocumentFilters;
import br.com.condominioauditoria.contracts.query.v2.DocumentChunkLocation;
import br.com.condominioauditoria.contracts.query.v2.DocumentSearchMode;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * rpc SearchDocuments end to end in process: mcp (stub) → api (QueryGrpcService with the real authentication) → fake
 * rag (Assistente). Checks the forwarded token, mode, limits, conversion, rejection of another condominium, second
 * barrier and rag down.
 */
class DocumentSearchGrpcTest {

    private static final UUID CONDOMINIUM_A = UUID.randomUUID();
    private static final UUID CONDOMINIUM_B = UUID.randomUUID();

    private final SourceFileRepository files = mock(SourceFileRepository.class);
    /** Mock: require does not throw = feature enabled. The disabled-feature tests set up the rejection. */
    private final FeatureService features = mock(FeatureService.class);
    private final UsageService usage = mock(UsageService.class);
    private final AiConfigurationService aiConfiguration = mock(AiConfigurationService.class);
    private final SourceFile minutesOfA = new SourceFile(CONDOMINIUM_A, FileCategory.MINUTES, "ata.pdf",
            "a/MINUTES/2026/x-ata.pdf",
            "a".repeat(64), 10, "application/pdf", "gestor");
    private final SourceFile budgetOfA = new SourceFile(CONDOMINIUM_A, FileCategory.PO, "po.xlsx",
            "a/PO/2026/x-po.xlsx",
            "c".repeat(64), 10, "application/vnd.ms-excel", "gestor");
    private final SourceFile minutesOfB = new SourceFile(CONDOMINIUM_B, FileCategory.MINUTES, "ata-b.pdf",
            "b/MINUTES/2026/x-ata-b.pdf",
            "b".repeat(64), 10, "application/pdf", "gestor");

    /** What the fake rag received and what it will return. */
    private final List<SearchRequest> ragRequests = new ArrayList<>();
    private final AtomicReference<String> tokenReceivedByRag = new AtomicReference<>();
    private final List<IndexedChunk> ragResponse = new ArrayList<>();

    private Server rag;
    private Server api;
    private ManagedChannel ragChannel;
    private ManagedChannel apiChannel;

    private final JwtDecoder decoder = token -> switch (token) {
        case "usuario-a" -> jwt(token, "usuario.a", List.of("USUARIO"), List.of(CONDOMINIUM_A.toString()));
        case "admin" -> jwt(token, "admin", List.of("ADMIN"), List.of());
        default -> throw new BadJwtException("assinatura inválida");
    };

    @BeforeEach
    void setUp() throws Exception {
        // Repository: only returns files of the requested condominium, like the real query
        when(files.findByCondominiumIdAndIdIn(eq(CONDOMINIUM_A), anyCollection())).thenAnswer(call -> {
            var ids = call.<java.util.Collection<UUID>>getArgument(1);
            return List.of(minutesOfA, budgetOfA, minutesOfB).stream()
                    .filter(a -> a.getCondominiumId().equals(CONDOMINIUM_A) && ids.contains(a.getId())).toList();
        });

        var assistant = new AssistantGrpc.AssistantImplBase() {
            @Override
            public void search(SearchRequest request, StreamObserver<SearchResponse> response) {
                ragRequests.add(request);
                response.onNext(SearchResponse.newBuilder().addAllChunks(ragResponse)
                        .setModeUsed(SearchMode.SEARCH_MODE_HYBRID).build());
                response.onCompleted();
            }
        };
        var tokenCapture = new ServerInterceptor() {
            @Override
            public <Q, R> ServerCall.Listener<Q> interceptCall(ServerCall<Q, R> call, Metadata headers,
                    ServerCallHandler<Q, R> next) {
                tokenReceivedByRag.set(headers.get(GrpcAuthInterceptor.AUTHORIZATION));
                return next.startCall(call, headers);
            }
        };
        String ragName = InProcessServerBuilder.generateName();
        rag = InProcessServerBuilder.forName(ragName)
                .addService(ServerInterceptors.intercept(assistant, tokenCapture)).build().start();
        ragChannel = InProcessChannelBuilder.forName(ragName).build();

        var properties = new ApiProperties(null, null, null, new ApiProperties.RagProperties("rag:9091", 5));
        var access = new CondominiumAccess();
        embeddings(AiMode.LOCAL, "bge-m3");
        var search = new DocumentSearchService(access, files, new AssistantClient(ragChannel, properties), features,
                usage, aiConfiguration);
        var query = new QueryGrpcService(new QueryService(access, null, files, null, null, null, null, search));

        var converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("preferred_username");
        converter.setJwtGrantedAuthoritiesConverter(jwt -> jwt.getClaimAsStringList("perfis").stream()
                .map(p -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + p)).toList());
        String apiName = InProcessServerBuilder.generateName();
        api = InProcessServerBuilder.forName(apiName)
                .addService(ServerInterceptors.intercept(query, new GrpcAuthInterceptor(decoder, converter)))
                .build().start();
        apiChannel = InProcessChannelBuilder.forName(apiName).build();
    }

    @AfterEach
    void tearDown() {
        apiChannel.shutdownNow();
        api.shutdownNow();
        ragChannel.shutdownNow();
        rag.shutdownNow();
    }

    @Test
    void forwardsToRagWithTokenAndConvertsChunks() {
        ragResponse.add(chunk(minutesOfA,
                ChunkLocation.newBuilder().setPage(PageLocation.newBuilder().setPage(3)).build(),
                0.9));
        ragResponse.add(chunk(budgetOfA, ChunkLocation.newBuilder().setSheet(SheetLocation.newBuilder()
                .setTab("Previsto").setStartRow(10).setEndRow(14)).build(), 0.5));

        SearchDocumentsResponse response = stub("usuario-a").searchDocuments(request(CONDOMINIUM_A, "  multa  ")
                .setFilters(DocumentFilters.newBuilder().addCategories("minutes").setDateFrom("2026-01-01"))
                .build());

        assertThat(tokenReceivedByRag.get()).isEqualTo("Bearer usuario-a");
        SearchRequest inRag = ragRequests.getFirst();
        assertThat(inRag.getCondominiumId()).isEqualTo(CONDOMINIUM_A.toString());
        assertThat(inRag.getText()).isEqualTo("multa");
        assertThat(inRag.getMode()).isEqualTo(SearchMode.SEARCH_MODE_HYBRID);
        assertThat(inRag.getLimit()).isEqualTo(10);
        assertThat(inRag.getFilters().getCategoriesList()).containsExactly("MINUTES");
        assertThat(inRag.getFilters().getDateFrom()).isEqualTo("2026-01-01");

        assertThat(response.getModeUsed()).isEqualTo(DocumentSearchMode.DOCUMENT_SEARCH_MODE_HYBRID);
        assertThat(response.getChunksList()).hasSize(2);
        var first = response.getChunks(0);
        assertThat(first.getFileId()).isEqualTo(minutesOfA.getId().toString());
        assertThat(first.getFileName()).isEqualTo("ata.pdf");
        assertThat(first.getSha256()).isEqualTo(minutesOfA.getSha256());
        assertThat(first.getText()).isEqualTo("texto de ata.pdf");
        assertThat(first.getLocation().getKindCase()).isEqualTo(DocumentChunkLocation.KindCase.PAGE);
        assertThat(first.getLocation().getPage().getPage()).isEqualTo(3);
        var second = response.getChunks(1).getLocation().getSheet();
        assertThat(second.getTab()).isEqualTo("Previsto");
        assertThat(second.getStartRow()).isEqualTo(10);
        assertThat(second.getEndRow()).isEqualTo(14);
    }

    @Test
    void featureOffIsFailedPreconditionWithoutCallingRagOrRecordingUsage() {
        doThrow(new FeatureNotEnabledException(FeatureService.ASSISTANT, "Assistente"))
                .when(features).require(CONDOMINIUM_A, FeatureService.ASSISTANT);

        assertThatThrownBy(() -> stub("usuario-a").searchDocuments(request(CONDOMINIUM_A, "multa").build()))
                .isInstanceOfSatisfying(StatusRuntimeException.class, e -> {
                    assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
                    assertThat(e.getStatus().getDescription())
                            .isEqualTo("Módulo Assistente não contratado para este condomínio.");
                });
        assertThat(ragRequests).isEmpty();
        verifyNoInteractions(usage);
    }

    @Test
    void withoutCondominiumAccessIsRejectedBeforeCheckingFeature() {
        assertThatThrownBy(() -> stub("usuario-a").searchDocuments(request(CONDOMINIUM_B, "multa").build()))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED));
        verifyNoInteractions(features);
    }

    @Test
    void eachAnsweredSearchRecordsAnMcpCallWithUser() {
        ragResponse.add(chunk(minutesOfA,
                ChunkLocation.newBuilder().setPage(PageLocation.newBuilder().setPage(1)).build(),
                0.9));

        stub("usuario-a").searchDocuments(request(CONDOMINIUM_A, "multa").build());
        stub("usuario-a").searchDocuments(request(CONDOMINIUM_A, "portão").build());

        verify(usage, times(2)).recordMcpCall(CONDOMINIUM_A, "usuario.a", true);
    }

    @Test
    void searchFailingInRagDoesNotRecordUsage() {
        rag.shutdownNow();

        assertThatThrownBy(() -> stub("usuario-a").searchDocuments(request(CONDOMINIUM_A, "multa").build()))
                .isInstanceOf(StatusRuntimeException.class);
        verifyNoInteractions(usage);
    }

    @Test
    void secondBarrierDropsChunkFromOtherCondominiumOrUnknownFile() {
        var page = ChunkLocation.newBuilder().setPage(PageLocation.newBuilder().setPage(1)).build();
        ragResponse.add(chunk(minutesOfB, page, 0.99));                  // file of another condominium
        ragResponse.add(chunk(minutesOfA, page, 0.8));                   // ok
        ragResponse.add(IndexedChunk.newBuilder().setChunkId("x").setFileId(UUID.randomUUID().toString())
                .setLocation(page).build());                          // file that does not exist in the api
        ragResponse.add(IndexedChunk.newBuilder().setChunkId("y").setFileId("nao-e-uuid").build());

        var response = stub("usuario-a").searchDocuments(request(CONDOMINIUM_A, "multa").build());

        assertThat(response.getChunksList()).extracting(t -> t.getFileId())
                .containsExactly(minutesOfA.getId().toString());
    }

    @Test
    void otherCondominiumIsRejectedWithoutCallingRag() {
        assertThatThrownBy(() -> stub("usuario-a").searchDocuments(request(CONDOMINIUM_B, "multa").build()))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED));
        assertThat(ragRequests).isEmpty();
    }

    @Test
    void withoutTokenIsRejected() {
        assertThatThrownBy(() -> QueryGrpc.newBlockingStub(apiChannel)
                .searchDocuments(request(CONDOMINIUM_A, "multa").build()))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED));
        assertThat(ragRequests).isEmpty();
    }

    @Test
    void emptyTextIsInvalid() {
        assertThatThrownBy(() -> stub("usuario-a").searchDocuments(request(CONDOMINIUM_A, "   ").build()))
                .isInstanceOfSatisfying(StatusRuntimeException.class, e -> {
                    assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
                    assertThat(e.getStatus().getDescription()).contains("texto");
                });
        assertThat(ragRequests).isEmpty();
    }

    @Test
    void negativeLimitAndBadDateAreInvalid() {
        assertThatThrownBy(() -> stub("usuario-a").searchDocuments(request(CONDOMINIUM_A,
                "multa").setLimit(-1).build()))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT));
        assertThatThrownBy(() -> stub("usuario-a").searchDocuments(request(CONDOMINIUM_A, "multa")
                .setFilters(DocumentFilters.newBuilder().setDateTo("30/09/2026")).build()))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT));
        assertThat(ragRequests).isEmpty();
    }

    @Test
    void embeddingsOffSearchByWordOnlyWithoutModel() {
        embeddings(AiMode.OFF, null);

        stub("usuario-a").searchDocuments(request(CONDOMINIUM_A, "multa").build());

        assertThat(ragRequests.getFirst().getMode()).isEqualTo(SearchMode.SEARCH_MODE_KEYWORD);
        assertThat(ragRequests.getFirst().getEmbeddingModel()).isEmpty();
    }

    @Test
    void localEmbeddingsSearchHybridWithConfiguredModel() {
        stub("usuario-a").searchDocuments(request(CONDOMINIUM_A, "multa").build());

        assertThat(ragRequests.getFirst().getMode()).isEqualTo(SearchMode.SEARCH_MODE_HYBRID);
        assertThat(ragRequests.getFirst().getEmbeddingModel()).isEqualTo("bge-m3");
    }

    private void embeddings(AiMode mode, String model) {
        var answers = new AiConfigurationService.Answers(null, AiMode.EXTERNAL_MCP, null, null, null, null);
        when(aiConfiguration.read(any())).thenReturn(new AiConfigurationService.Effective(AiMode.EXTERNAL_MCP, answers,
                new AiConfigurationService.Embeddings(mode, mode == AiMode.LOCAL ? "ollama-local" : null, model), null,
                null));
    }

    @Test
    void limitAboveMaxIsReduced() {
        stub("admin").searchDocuments(request(CONDOMINIUM_A, "multa").setLimit(80).build());
        assertThat(ragRequests.getFirst().getLimit()).isEqualTo(50);
    }

    @Test
    void ragDownIsUnavailableWithReadableMessage() {
        rag.shutdownNow();

        assertThatThrownBy(() -> stub("usuario-a").searchDocuments(request(CONDOMINIUM_A, "multa").build()))
                .isInstanceOfSatisfying(StatusRuntimeException.class, e -> {
                    assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE);
                    assertThat(e.getStatus().getDescription()).contains("Busca nos documentos indisponível");
                });
    }

    private QueryGrpc.QueryBlockingStub stub(String token) {
        var headers = new Metadata();
        headers.put(GrpcAuthInterceptor.AUTHORIZATION, "Bearer " + token);
        return QueryGrpc.newBlockingStub(apiChannel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    private static SearchDocumentsRequest.Builder request(UUID condominiumId, String text) {
        return SearchDocumentsRequest.newBuilder().setCondominiumId(condominiumId.toString()).setText(text);
    }

    private static IndexedChunk chunk(SourceFile file, ChunkLocation location, double score) {
        return IndexedChunk.newBuilder()
                .setChunkId(UUID.randomUUID().toString())
                .setFileId(file.getId().toString())
                .setFileName(file.getOriginalName())
                .setCategory(file.getCategory().name())
                .setLocation(location)
                .setText("texto de " + file.getOriginalName())
                .setScore(score)
                .setSha256(file.getSha256())
                .build();
    }

    private static Jwt jwt(String token, String username, List<String> roles, List<String> condominiums) {
        return new Jwt(token, Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "none"),
                Map.of("preferred_username", username, "perfis", roles, "condominios", condominiums));
    }
}
