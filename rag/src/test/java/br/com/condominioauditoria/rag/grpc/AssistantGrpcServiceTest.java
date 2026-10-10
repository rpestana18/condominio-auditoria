package br.com.condominioauditoria.rag.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.contratos.assistente.v1.AssistenteGrpc;
import br.com.condominioauditoria.contratos.assistente.v1.BuscarRequest;
import br.com.condominioauditoria.contratos.assistente.v1.BuscarResponse;
import br.com.condominioauditoria.contratos.assistente.v1.FiltrosBusca;
import br.com.condominioauditoria.contratos.assistente.v1.ModoBusca;
import br.com.condominioauditoria.rag.repository.IndexRepository;
import br.com.condominioauditoria.rag.search.DocumentSearch;
import br.com.condominioauditoria.rag.search.EmbeddingGenerator;
import br.com.condominioauditoria.rag.search.FoundChunk;
import br.com.condominioauditoria.rag.search.Location;
import br.com.condominioauditoria.rag.security.RagKeys;
import br.com.condominioauditoria.rag.service.ProviderCatalog;
import br.com.condominioauditoria.rag.service.QuestionService;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** gRPC contract Assistente.Buscar: input validation, filters passed on and location of each type. */
class AssistantGrpcServiceTest {

    private static final String CONDOMINIUM = "6f1d2c1e-3b4a-4c8e-9a51-2815a0000001";

    private final DocumentSearch search = mock(DocumentSearch.class);
    private final EmbeddingGenerator embeddings = mock(EmbeddingGenerator.class);
    private Server server;
    private ManagedChannel channel;
    private AssistenteGrpc.AssistenteBlockingStub client;

    @BeforeEach
    void start() throws Exception {
        when(embeddings.accepts(anyString())).thenAnswer(i -> {
            String m = i.getArgument(0);
            return m.isEmpty() || m.equals("bge-m3");
        });
        when(embeddings.model()).thenReturn("bge-m3");
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(new AssistantGrpcService(search, embeddings, mock(QuestionService.class),
                        mock(ProviderCatalog.class), mock(RagKeys.class)))
                .build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        client = AssistenteGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void stop() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    void returnsChunksWithLocationAndModeUsed() {
        UUID file = UUID.randomUUID();
        when(search.search(any(), eq("multa"), eq(DocumentSearch.Mode.HYBRID), eq(0))).thenReturn(
                new DocumentSearch.Result(List.of(
                        chunk(file, new Location.Page(3)),
                        chunk(file, new Location.Sheet("Jan", 2, 31)),
                        chunk(file, new Location.Paragraphs(4, 7, ""))),
                        DocumentSearch.Mode.KEYWORD));

        BuscarResponse response = client.buscar(BuscarRequest.newBuilder().setCondominioId(CONDOMINIUM)
                .setTexto("multa").build());

        assertThat(response.getModoUsado()).isEqualTo(ModoBusca.MODO_BUSCA_PALAVRA);
        assertThat(response.getTrechosList()).hasSize(3);
        assertThat(response.getTrechos(0).getLocalizacao().getPagina().getPagina()).isEqualTo(3);
        assertThat(response.getTrechos(1).getLocalizacao().getPlanilha().getAba()).isEqualTo("Jan");
        assertThat(response.getTrechos(1).getLocalizacao().getPlanilha().getLinhaFim()).isEqualTo(31);
        assertThat(response.getTrechos(2).getLocalizacao().getParagrafos().getParagrafoInicio()).isEqualTo(4);
        assertThat(response.getTrechos(0).getArquivoId()).isEqualTo(file.toString());
        assertThat(response.getTrechos(0).getSha256()).hasSize(64);
    }

    @Test
    void filtersGoToSearch() {
        UUID file = UUID.randomUUID();
        when(search.search(any(), anyString(), any(), anyInt()))
                .thenReturn(new DocumentSearch.Result(List.of(), DocumentSearch.Mode.KEYWORD));

        client.buscar(BuscarRequest.newBuilder().setCondominioId(CONDOMINIUM).setTexto("\"fundo de reserva\"")
                .setModo(ModoBusca.MODO_BUSCA_PALAVRA).setLimite(5)
                .setFiltros(FiltrosBusca.newBuilder().addCategorias("ATA").setDataInicio("2026-01-01")
                        .addArquivoIds(file.toString()))
                .build());

        ArgumentCaptor<IndexRepository.SearchFilters> filters = ArgumentCaptor.forClass(
                IndexRepository.SearchFilters.class);
        verify(search).search(filters.capture(), eq("\"fundo de reserva\""), eq(DocumentSearch.Mode.KEYWORD), eq(5));
        assertThat(filters.getValue().condominiumId()).isEqualTo(UUID.fromString(CONDOMINIUM));
        assertThat(filters.getValue().categories()).containsExactly("ATA");
        assertThat(filters.getValue().dateFrom()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(filters.getValue().dateTo()).isNull();
        assertThat(filters.getValue().fileIds()).containsExactly(file);
    }

    @Test
    void invalidInputIsInvalidArgument() {
        assertInvalid(BuscarRequest.newBuilder().setCondominioId(CONDOMINIUM).setTexto("  ").build(), "Texto");
        assertInvalid(BuscarRequest.newBuilder().setCondominioId("x").setTexto("a").build(), "condominio_id");
        assertInvalid(BuscarRequest.newBuilder().setTexto("a").build(), "condominio_id");
        assertInvalid(BuscarRequest.newBuilder().setCondominioId(CONDOMINIUM).setTexto("a").setLimite(-1).build(),
                "Limite");
        assertInvalid(BuscarRequest.newBuilder().setCondominioId(CONDOMINIUM).setTexto("a")
                .setFiltros(FiltrosBusca.newBuilder().setDataFim("30/09/2026")).build(), "data_fim");
        assertInvalid(BuscarRequest.newBuilder().setCondominioId(CONDOMINIUM).setTexto("a")
                .setModeloEmbeddings("outro").build(), "outro");
    }

    private void assertInvalid(BuscarRequest request, String chunk) {
        assertThatThrownBy(() -> client.buscar(request))
                .isInstanceOfSatisfying(StatusRuntimeException.class, e -> {
                    assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
                    assertThat(e.getStatus().getDescription()).contains(chunk);
                });
    }

    private static FoundChunk chunk(UUID file, Location local) {
        return new FoundChunk(UUID.randomUUID(), file, "a.pdf", "ATA", local, "texto", 0.5, "a".repeat(64));
    }
}
