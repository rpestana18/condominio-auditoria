package br.com.condominioauditoria.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.condominioauditoria.contratos.consulta.v1.BuscarDocumentosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.BuscarDocumentosResponse;
import br.com.condominioauditoria.contratos.consulta.v1.ConsultaGrpc;
import br.com.condominioauditoria.contratos.consulta.v1.LocalPagina;
import br.com.condominioauditoria.contratos.consulta.v1.LocalParagrafos;
import br.com.condominioauditoria.contratos.consulta.v1.LocalPlanilha;
import br.com.condominioauditoria.contratos.consulta.v1.LocalizacaoTrecho;
import br.com.condominioauditoria.contratos.consulta.v1.ModoBuscaDocumentos;
import br.com.condominioauditoria.contratos.consulta.v1.TrechoDocumento;
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

    private final AtomicReference<BuscarDocumentosRequest> receivedRequest = new AtomicReference<>();
    private final AtomicReference<String> receivedToken = new AtomicReference<>();
    private final AtomicReference<Status> errorToReturn = new AtomicReference<>();
    private Server server;
    private ManagedChannel channel;
    private CondominiumTools tools;

    @BeforeEach
    void start() throws Exception {
        String name = InProcessServerBuilder.generateName();
        var fakeApi = new ConsultaGrpc.ConsultaImplBase() {
            @Override
            public void buscarDocumentos(BuscarDocumentosRequest request,
                    StreamObserver<BuscarDocumentosResponse> response) {
                receivedRequest.set(request);
                if (errorToReturn.get() != null) {
                    response.onError(errorToReturn.get().asRuntimeException());
                    return;
                }
                response.onNext(BuscarDocumentosResponse.newBuilder()
                        .setModoUsado(ModoBuscaDocumentos.MODO_BUSCA_DOCUMENTOS_PALAVRA)
                        .addTrechos(chunk("ata-2025.pdf", "ATA", LocalizacaoTrecho.newBuilder()
                                .setPagina(LocalPagina.newBuilder().setPagina(3)).build()))
                        .addTrechos(chunk("previsao.xlsx", "PO", LocalizacaoTrecho.newBuilder()
                                .setPlanilha(LocalPlanilha.newBuilder().setAba("Plan1").setLinhaInicio(2)
                                        .setLinhaFim(31)).build()))
                        .addTrechos(chunk("contrato.docx", "CONTRATO", LocalizacaoTrecho.newBuilder()
                                .setParagrafos(LocalParagrafos.newBuilder().setParagrafoInicio(4)
                                        .setParagrafoFim(7)).build()))
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
                List.of("ATA", " ", "CONTRATO"), "2025-01-01", "2025-12-31", null, null);

        assertThat(receivedToken.get()).isEqualTo(TOKEN);
        var request = receivedRequest.get();
        assertThat(request.getCondominioId()).isEqualTo(CONDOMINIUM);
        assertThat(request.getTexto()).isEqualTo("\"reajuste da taxa\" -2023");
        assertThat(request.getLimite()).isEqualTo(10);
        assertThat(request.getFiltros().getCategoriasList()).containsExactly("ATA", "CONTRATO");
        assertThat(request.getFiltros().getDataInicio()).isEqualTo("2025-01-01");
        assertThat(request.getFiltros().getDataFim()).isEqualTo("2025-12-31");
        assertThat(request.getFiltros().getArquivoIdsList()).isEmpty();

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

        assertThat(receivedRequest.get().getLimite()).isEqualTo(50);
        assertThat(receivedRequest.get().getFiltros().getArquivoIdsList()).containsExactly("abc");
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
        assertThat(CondominiumTools.readableLocation(LocalizacaoTrecho.newBuilder()
                .setPlanilha(LocalPlanilha.newBuilder().setAba("Resumo").setLinhaInicio(5).setLinhaFim(5)).build()))
                .isEqualTo("aba Resumo, linha 5");
        assertThat(CondominiumTools.readableLocation(LocalizacaoTrecho.newBuilder()
                .setParagrafos(LocalParagrafos.newBuilder().setParagrafoInicio(2).setParagrafoFim(2)
                        .setSecao("Cláusula 5")).build()))
                .isEqualTo("parágrafo 2, seção Cláusula 5");
        assertThat(CondominiumTools.readableLocation(LocalizacaoTrecho.getDefaultInstance()))
                .isEqualTo("localização não informada");
    }

    private void search() {
        tools.searchDocuments(CONTEXT, CONDOMINIUM, "taxa", null, null, null, null, null);
    }

    private static TrechoDocumento chunk(String name, String category, LocalizacaoTrecho location) {
        return TrechoDocumento.newBuilder().setTrechoId("t-" + name).setArquivoId("a-" + name).setNomeArquivo(name)
                .setCategoria(category).setLocalizacao(location).setTexto("texto de " + name).setPontuacao(1.5)
                .setSha256("abc").build();
    }
}
