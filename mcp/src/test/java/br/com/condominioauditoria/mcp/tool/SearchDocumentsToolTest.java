package br.com.condominioauditoria.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.condominioauditoria.contracts.query.v2.SearchDocumentsRequest;
import br.com.condominioauditoria.contracts.query.v2.SearchDocumentsResponse;
import br.com.condominioauditoria.contracts.query.v2.QueryGrpc;
import br.com.condominioauditoria.contracts.query.v2.PageLocation;
import br.com.condominioauditoria.contracts.query.v2.ParagraphsLocation;
import br.com.condominioauditoria.contracts.query.v2.SheetLocation;
import br.com.condominioauditoria.contracts.query.v2.DocumentChunkLocation;
import br.com.condominioauditoria.contracts.query.v2.DocumentSearchMode;
import br.com.condominioauditoria.contracts.query.v2.DocumentChunk;
import br.com.condominioauditoria.mcp.client.ApiClient;
import br.com.condominioauditoria.mcp.config.McpConfig;
import br.com.condominioauditoria.mcp.config.properties.McpProperties;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.modelcontextprotocol.common.McpTransportContext;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tool buscar_documentos against a fake in-memory api (in-process gRPC). */
class SearchDocumentsToolTest {

    private static final String CONDOMINIUM = "6f1d2c1e-3b4a-4c8e-9a51-2815a0000001";
    private static final String TOKEN = "Bearer token-do-gestor";
    private static final McpTransportContext CONTEXT =
            McpTransportContext.create(Map.of(McpConfig.AUTHORIZATION, TOKEN));

    private final AtomicReference<SearchDocumentsRequest> receivedRequest = new AtomicReference<>();
    private final AtomicReference<String> receivedToken = new AtomicReference<>();
    private final AtomicReference<Status> errorToReturn = new AtomicReference<>();
    private Server server;
    private ManagedChannel channel;
    private CondominiumTools tools;

    @BeforeEach
    void start() throws Exception {
        String name = InProcessServerBuilder.generateName();
        var fakeApi = new QueryGrpc.QueryImplBase() {
            @Override
            public void searchDocuments(SearchDocumentsRequest request,
                    StreamObserver<SearchDocumentsResponse> response) {
                receivedRequest.set(request);
                if (errorToReturn.get() != null) {
                    response.onError(errorToReturn.get().asRuntimeException());
                    return;
                }
                response.onNext(SearchDocumentsResponse.newBuilder()
                        .setModeUsed(DocumentSearchMode.DOCUMENT_SEARCH_MODE_KEYWORD)
                        .addChunks(chunk("ata-2025.pdf", "MINUTES", DocumentChunkLocation.newBuilder()
                                .setPage(PageLocation.newBuilder().setPage(3)).build()))
                        .addChunks(chunk("previsao.xlsx", "PO", DocumentChunkLocation.newBuilder()
                                .setSheet(SheetLocation.newBuilder().setTab("Plan1").setStartRow(2)
                                        .setEndRow(31)).build()))
                        .addChunks(chunk("contrato.docx", "CONTRACT", DocumentChunkLocation.newBuilder()
                                .setParagraphs(ParagraphsLocation.newBuilder().setParagraphStart(4)
                                        .setParagraphEnd(7)).build()))
                        .build());
                response.onCompleted();
            }
        };
        ServerInterceptor tokenRecorder = new ServerInterceptor() {
            @Override
            public <Q, R> ServerCall.Listener<Q> interceptCall(ServerCall<Q, R> call, Metadata headers,
                    ServerCallHandler<Q, R> next) {
                receivedToken.set(headers.get(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)));
                return next.startCall(call, headers);
            }
        };
        server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(ServerInterceptors.intercept(fakeApi, tokenRecorder)).build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        var properties = new McpProperties(new McpProperties.Api("em-memoria", 5));
        tools = new CondominiumTools(new ApiClient(channel, properties));
    }

    @AfterEach
    void stop() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    void passesFiltersAndTokenAndReturnsReadableLocation() {
        var result = tools.searchDocuments(CONTEXT, CONDOMINIUM, " \"reajuste da taxa\" -2023 ",
                List.of("MINUTES", " ", "CONTRACT"), "2025-01-01", "2025-12-31", null, null);

        assertThat(receivedToken.get()).isEqualTo(TOKEN);
        var request = receivedRequest.get();
        assertThat(request.getCondominiumId()).isEqualTo(CONDOMINIUM);
        assertThat(request.getText()).isEqualTo("\"reajuste da taxa\" -2023");
        assertThat(request.getLimit()).isEqualTo(10);
        assertThat(request.getFilters().getCategoriesList()).containsExactly("MINUTES", "CONTRACT");
        assertThat(request.getFilters().getDateFrom()).isEqualTo("2025-01-01");
        assertThat(request.getFilters().getDateTo()).isEqualTo("2025-12-31");
        assertThat(request.getFilters().getFileIdsList()).isEmpty();

        assertThat(result.modeUsed()).isEqualTo("PALAVRA");
        assertThat(result.total()).isEqualTo(3);
        assertThat(result.chunks()).extracting(CondominiumTools.FoundChunk::location)
                .containsExactly("página 3", "aba Plan1, linhas 2–31", "parágrafos 4–7");
        assertThat(result.chunks().getFirst().page()).isEqualTo(3);
        assertThat(result.chunks().get(1).page()).isNull();
        assertThat(result.chunks().getFirst().document()).isEqualTo("ata-2025.pdf");
        assertThat(result.warning()).contains("não conferido");
    }

    @Test
    void limitAboveMaximumBecomesFifty() {
        tools.searchDocuments(CONTEXT, CONDOMINIUM, "elevador", null, null, null, List.of("abc"), 500);

        assertThat(receivedRequest.get().getLimit()).isEqualTo(50);
        assertThat(receivedRequest.get().getFilters().getFileIdsList()).containsExactly("abc");
    }

    @Test
    void emptyTextAndInvalidLimitAreRejectedWithoutCallingApi() {
        assertThatThrownBy(() -> tools.searchDocuments(CONTEXT, CONDOMINIUM, "  ", null, null, null, null,
                null)).isInstanceOf(IllegalArgumentException.class).hasMessage("Informe o texto da busca");
        assertThatThrownBy(() -> tools.searchDocuments(CONTEXT, CONDOMINIUM, "taxa", null, null, null, null,
                0)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("de 1 a 50");
        assertThat(receivedRequest.get()).isNull();
    }

    @Test
    void withoutTokenRejects() {
        assertThatThrownBy(() -> tools.searchDocuments(McpTransportContext.EMPTY, CONDOMINIUM, "taxa", null,
                null, null, null, null)).hasMessageContaining("sem token");
    }

    @Test
    void apiErrorsBecomeReadableMessages() {
        errorToReturn.set(Status.FAILED_PRECONDITION
                .withDescription("módulo Assistente não contratado para este condomínio"));
        assertThatThrownBy(() -> search()).isInstanceOf(IllegalStateException.class)
                .hasMessage("módulo Assistente não contratado para este condomínio");

        errorToReturn.set(Status.UNAVAILABLE.withDescription("Busca nos documentos indisponível no momento"));
        assertThatThrownBy(() -> search()).hasMessage("Busca nos documentos indisponível no momento");

        errorToReturn.set(Status.INVALID_ARGUMENT.withDescription("Categoria desconhecida: X"));
        assertThatThrownBy(() -> search()).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Categoria desconhecida: X");

        errorToReturn.set(Status.UNIMPLEMENTED);
        assertThatThrownBy(() -> search()).hasMessage("Função indisponível nesta versão do backend");

        errorToReturn.set(Status.PERMISSION_DENIED.withDescription("Sem acesso ao condomínio"));
        assertThatThrownBy(() -> search()).hasMessage("Sem acesso ao condomínio");
    }

    @Test
    void locationOfSingleRowAndSection() {
        assertThat(CondominiumTools.readableLocation(DocumentChunkLocation.newBuilder()
                .setSheet(SheetLocation.newBuilder().setTab("Resumo").setStartRow(5).setEndRow(5)).build()))
                .isEqualTo("aba Resumo, linha 5");
        assertThat(CondominiumTools.readableLocation(DocumentChunkLocation.newBuilder()
                .setParagraphs(ParagraphsLocation.newBuilder().setParagraphStart(2).setParagraphEnd(2)
                        .setSection("Cláusula 5")).build()))
                .isEqualTo("parágrafo 2, seção Cláusula 5");
        assertThat(CondominiumTools.readableLocation(DocumentChunkLocation.getDefaultInstance()))
                .isEqualTo("localização não informada");
    }

    private void search() {
        tools.searchDocuments(CONTEXT, CONDOMINIUM, "taxa", null, null, null, null, null);
    }

    private static DocumentChunk chunk(String name, String category, DocumentChunkLocation location) {
        return DocumentChunk.newBuilder().setChunkId("t-" + name).setFileId("a-" + name).setFileName(name)
                .setCategory(category).setLocation(location).setText("texto de " + name).setScore(1.5)
                .setSha256("abc").build();
    }
}
