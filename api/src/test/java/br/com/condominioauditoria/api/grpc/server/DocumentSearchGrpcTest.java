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
import br.com.condominioauditoria.contratos.assistente.v1.AssistenteGrpc;
import br.com.condominioauditoria.contratos.assistente.v1.BuscarRequest;
import br.com.condominioauditoria.contratos.assistente.v1.BuscarResponse;
import br.com.condominioauditoria.contratos.assistente.v1.LocalPagina;
import br.com.condominioauditoria.contratos.assistente.v1.LocalPlanilha;
import br.com.condominioauditoria.contratos.assistente.v1.Localizacao;
import br.com.condominioauditoria.contratos.assistente.v1.ModoBusca;
import br.com.condominioauditoria.contratos.assistente.v1.Trecho;
import br.com.condominioauditoria.contratos.consulta.v1.BuscarDocumentosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.BuscarDocumentosResponse;
import br.com.condominioauditoria.contratos.consulta.v1.ConsultaGrpc;
import br.com.condominioauditoria.contratos.consulta.v1.FiltrosDocumentos;
import br.com.condominioauditoria.contratos.consulta.v1.LocalizacaoTrecho;
import br.com.condominioauditoria.contratos.consulta.v1.ModoBuscaDocumentos;
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
 * rpc BuscarDocumentos end to end in process: mcp (stub) → api (QueryGrpcService with the real authentication) → fake
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
    private final List<BuscarRequest> ragRequests = new ArrayList<>();
    private final AtomicReference<String> tokenReceivedByRag = new AtomicReference<>();
    private final List<Trecho> ragResponse = new ArrayList<>();

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

        var assistant = new AssistenteGrpc.AssistenteImplBase() {
            @Override
            public void buscar(BuscarRequest request, StreamObserver<BuscarResponse> response) {
                ragRequests.add(request);
                response.onNext(BuscarResponse.newBuilder().addAllTrechos(ragResponse)
                        .setModoUsado(ModoBusca.MODO_BUSCA_HIBRIDA).build());
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
                Localizacao.newBuilder().setPagina(LocalPagina.newBuilder().setPagina(3)).build(),
                0.9));
        ragResponse.add(chunk(budgetOfA, Localizacao.newBuilder().setPlanilha(LocalPlanilha.newBuilder()
                .setAba("Previsto").setLinhaInicio(10).setLinhaFim(14)).build(), 0.5));

        BuscarDocumentosResponse response = stub("usuario-a").buscarDocumentos(request(CONDOMINIUM_A, "  multa  ")
                .setFiltros(FiltrosDocumentos.newBuilder().addCategorias("minutes").setDataInicio("2026-01-01"))
                .build());

        assertThat(tokenReceivedByRag.get()).isEqualTo("Bearer usuario-a");
        BuscarRequest inRag = ragRequests.getFirst();
        assertThat(inRag.getCondominioId()).isEqualTo(CONDOMINIUM_A.toString());
        assertThat(inRag.getTexto()).isEqualTo("multa");
        assertThat(inRag.getModo()).isEqualTo(ModoBusca.MODO_BUSCA_HIBRIDA);
        assertThat(inRag.getLimite()).isEqualTo(10);
        assertThat(inRag.getFiltros().getCategoriasList()).containsExactly("MINUTES");
        assertThat(inRag.getFiltros().getDataInicio()).isEqualTo("2026-01-01");

        assertThat(response.getModoUsado()).isEqualTo(ModoBuscaDocumentos.MODO_BUSCA_DOCUMENTOS_HIBRIDA);
        assertThat(response.getTrechosList()).hasSize(2);
        var first = response.getTrechos(0);
        assertThat(first.getArquivoId()).isEqualTo(minutesOfA.getId().toString());
        assertThat(first.getNomeArquivo()).isEqualTo("ata.pdf");
        assertThat(first.getSha256()).isEqualTo(minutesOfA.getSha256());
        assertThat(first.getTexto()).isEqualTo("texto de ata.pdf");
        assertThat(first.getLocalizacao().getTipoCase()).isEqualTo(LocalizacaoTrecho.TipoCase.PAGINA);
        assertThat(first.getLocalizacao().getPagina().getPagina()).isEqualTo(3);
        var second = response.getTrechos(1).getLocalizacao().getPlanilha();
        assertThat(second.getAba()).isEqualTo("Previsto");
        assertThat(second.getLinhaInicio()).isEqualTo(10);
        assertThat(second.getLinhaFim()).isEqualTo(14);
    }

    @Test
    void featureOffIsFailedPreconditionWithoutCallingRagOrRecordingUsage() {
        doThrow(new FeatureNotEnabledException(FeatureService.ASSISTANT, "Assistente"))
                .when(features).require(CONDOMINIUM_A, FeatureService.ASSISTANT);

        assertThatThrownBy(() -> stub("usuario-a").buscarDocumentos(request(CONDOMINIUM_A, "multa").build()))
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
        assertThatThrownBy(() -> stub("usuario-a").buscarDocumentos(request(CONDOMINIUM_B, "multa").build()))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED));
        verifyNoInteractions(features);
    }

    @Test
    void eachAnsweredSearchRecordsAnMcpCallWithUser() {
        ragResponse.add(chunk(minutesOfA,
                Localizacao.newBuilder().setPagina(LocalPagina.newBuilder().setPagina(1)).build(),
                0.9));

        stub("usuario-a").buscarDocumentos(request(CONDOMINIUM_A, "multa").build());
        stub("usuario-a").buscarDocumentos(request(CONDOMINIUM_A, "portão").build());

        verify(usage, times(2)).recordMcpCall(CONDOMINIUM_A, "usuario.a", true);
    }

    @Test
    void searchFailingInRagDoesNotRecordUsage() {
        rag.shutdownNow();

        assertThatThrownBy(() -> stub("usuario-a").buscarDocumentos(request(CONDOMINIUM_A, "multa").build()))
                .isInstanceOf(StatusRuntimeException.class);
        verifyNoInteractions(usage);
    }

    @Test
    void secondBarrierDropsChunkFromOtherCondominiumOrUnknownFile() {
        var page = Localizacao.newBuilder().setPagina(LocalPagina.newBuilder().setPagina(1)).build();
        ragResponse.add(chunk(minutesOfB, page, 0.99));                  // file of another condominium
        ragResponse.add(chunk(minutesOfA, page, 0.8));                   // ok
        ragResponse.add(Trecho.newBuilder().setTrechoId("x").setArquivoId(UUID.randomUUID().toString())
                .setLocalizacao(page).build());                          // file that does not exist in the api
        ragResponse.add(Trecho.newBuilder().setTrechoId("y").setArquivoId("nao-e-uuid").build());

        var response = stub("usuario-a").buscarDocumentos(request(CONDOMINIUM_A, "multa").build());

        assertThat(response.getTrechosList()).extracting(t -> t.getArquivoId())
                .containsExactly(minutesOfA.getId().toString());
    }

    @Test
    void otherCondominiumIsRejectedWithoutCallingRag() {
        assertThatThrownBy(() -> stub("usuario-a").buscarDocumentos(request(CONDOMINIUM_B, "multa").build()))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED));
        assertThat(ragRequests).isEmpty();
    }

    @Test
    void withoutTokenIsRejected() {
        assertThatThrownBy(() -> ConsultaGrpc.newBlockingStub(apiChannel)
                .buscarDocumentos(request(CONDOMINIUM_A, "multa").build()))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED));
        assertThat(ragRequests).isEmpty();
    }

    @Test
    void emptyTextIsInvalid() {
        assertThatThrownBy(() -> stub("usuario-a").buscarDocumentos(request(CONDOMINIUM_A, "   ").build()))
                .isInstanceOfSatisfying(StatusRuntimeException.class, e -> {
                    assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
                    assertThat(e.getStatus().getDescription()).contains("texto");
                });
        assertThat(ragRequests).isEmpty();
    }

    @Test
    void negativeLimitAndBadDateAreInvalid() {
        assertThatThrownBy(() -> stub("usuario-a").buscarDocumentos(request(CONDOMINIUM_A,
                "multa").setLimite(-1).build()))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT));
        assertThatThrownBy(() -> stub("usuario-a").buscarDocumentos(request(CONDOMINIUM_A, "multa")
                .setFiltros(FiltrosDocumentos.newBuilder().setDataFim("30/09/2026")).build()))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT));
        assertThat(ragRequests).isEmpty();
    }

    @Test
    void embeddingsOffSearchByWordOnlyWithoutModel() {
        embeddings(AiMode.OFF, null);

        stub("usuario-a").buscarDocumentos(request(CONDOMINIUM_A, "multa").build());

        assertThat(ragRequests.getFirst().getModo()).isEqualTo(ModoBusca.MODO_BUSCA_PALAVRA);
        assertThat(ragRequests.getFirst().getModeloEmbeddings()).isEmpty();
    }

    @Test
    void localEmbeddingsSearchHybridWithConfiguredModel() {
        stub("usuario-a").buscarDocumentos(request(CONDOMINIUM_A, "multa").build());

        assertThat(ragRequests.getFirst().getModo()).isEqualTo(ModoBusca.MODO_BUSCA_HIBRIDA);
        assertThat(ragRequests.getFirst().getModeloEmbeddings()).isEqualTo("bge-m3");
    }

    private void embeddings(AiMode mode, String model) {
        var answers = new AiConfigurationService.Answers(null, AiMode.EXTERNAL_MCP, null, null, null, null);
        when(aiConfiguration.read(any())).thenReturn(new AiConfigurationService.Effective(AiMode.EXTERNAL_MCP, answers,
                new AiConfigurationService.Embeddings(mode, mode == AiMode.LOCAL ? "ollama-local" : null, model), null,
                null));
    }

    @Test
    void limitAboveMaxIsReduced() {
        stub("admin").buscarDocumentos(request(CONDOMINIUM_A, "multa").setLimite(80).build());
        assertThat(ragRequests.getFirst().getLimite()).isEqualTo(50);
    }

    @Test
    void ragDownIsUnavailableWithReadableMessage() {
        rag.shutdownNow();

        assertThatThrownBy(() -> stub("usuario-a").buscarDocumentos(request(CONDOMINIUM_A, "multa").build()))
                .isInstanceOfSatisfying(StatusRuntimeException.class, e -> {
                    assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE);
                    assertThat(e.getStatus().getDescription()).contains("Busca nos documentos indisponível");
                });
    }

    private ConsultaGrpc.ConsultaBlockingStub stub(String token) {
        var headers = new Metadata();
        headers.put(GrpcAuthInterceptor.AUTHORIZATION, "Bearer " + token);
        return ConsultaGrpc.newBlockingStub(apiChannel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    private static BuscarDocumentosRequest.Builder request(UUID condominiumId, String text) {
        return BuscarDocumentosRequest.newBuilder().setCondominioId(condominiumId.toString()).setTexto(text);
    }

    private static Trecho chunk(SourceFile file, Localizacao location, double score) {
        return Trecho.newBuilder()
                .setTrechoId(UUID.randomUUID().toString())
                .setArquivoId(file.getId().toString())
                .setNomeArquivo(file.getOriginalName())
                .setCategoria(file.getCategory().name())
                .setLocalizacao(location)
                .setTexto("texto de " + file.getOriginalName())
                .setPontuacao(score)
                .setSha256(file.getSha256())
                .build();
    }

    private static Jwt jwt(String token, String username, List<String> roles, List<String> condominiums) {
        return new Jwt(token, Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "none"),
                Map.of("preferred_username", username, "perfis", roles, "condominios", condominiums));
    }
}
