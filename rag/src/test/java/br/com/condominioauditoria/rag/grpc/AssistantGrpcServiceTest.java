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

import br.com.condominioauditoria.contracts.assistant.v2.AssistantGrpc;
import br.com.condominioauditoria.contracts.assistant.v2.SearchRequest;
import br.com.condominioauditoria.contracts.assistant.v2.SearchResponse;
import br.com.condominioauditoria.contracts.assistant.v2.SearchFilters;
import br.com.condominioauditoria.contracts.assistant.v2.SearchMode;
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

/** gRPC contract Assistant.Search: input validation, filters passed on and location of each type. */
class AssistantGrpcServiceTest {

    private static final String CONDOMINIUM = "6f1d2c1e-3b4a-4c8e-9a51-2815a0000001";

    private final DocumentSearch search = mock(DocumentSearch.class);
    private final EmbeddingGenerator embeddings = mock(EmbeddingGenerator.class);
    private Server server;
    private ManagedChannel channel;
    private AssistantGrpc.AssistantBlockingStub client;

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
        client = AssistantGrpc.newBlockingStub(channel);
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

        SearchResponse response = client.search(SearchRequest.newBuilder().setCondominiumId(CONDOMINIUM)
                .setText("multa").build());

        assertThat(response.getModeUsed()).isEqualTo(SearchMode.SEARCH_MODE_KEYWORD);
        assertThat(response.getChunksList()).hasSize(3);
        assertThat(response.getChunks(0).getLocation().getPage().getPage()).isEqualTo(3);
        assertThat(response.getChunks(1).getLocation().getSheet().getTab()).isEqualTo("Jan");
        assertThat(response.getChunks(1).getLocation().getSheet().getEndRow()).isEqualTo(31);
        assertThat(response.getChunks(2).getLocation().getParagraphs().getParagraphStart()).isEqualTo(4);
        assertThat(response.getChunks(0).getFileId()).isEqualTo(file.toString());
        assertThat(response.getChunks(0).getSha256()).hasSize(64);
    }

    @Test
    void filtersGoToSearch() {
        UUID file = UUID.randomUUID();
        when(search.search(any(), anyString(), any(), anyInt()))
                .thenReturn(new DocumentSearch.Result(List.of(), DocumentSearch.Mode.KEYWORD));

        client.search(SearchRequest.newBuilder().setCondominiumId(CONDOMINIUM).setText("\"fundo de reserva\"")
                .setMode(SearchMode.SEARCH_MODE_KEYWORD).setLimit(5)
                .setFilters(SearchFilters.newBuilder().addCategories("MINUTES").setDateFrom("2026-01-01")
                        .addFileIds(file.toString()))
                .build());

        ArgumentCaptor<IndexRepository.SearchFilters> filters = ArgumentCaptor.forClass(
                IndexRepository.SearchFilters.class);
        verify(search).search(filters.capture(), eq("\"fundo de reserva\""), eq(DocumentSearch.Mode.KEYWORD), eq(5));
        assertThat(filters.getValue().condominiumId()).isEqualTo(UUID.fromString(CONDOMINIUM));
        assertThat(filters.getValue().categories()).containsExactly("MINUTES");
        assertThat(filters.getValue().dateFrom()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(filters.getValue().dateTo()).isNull();
        assertThat(filters.getValue().fileIds()).containsExactly(file);
    }

    @Test
    void invalidInputIsInvalidArgument() {
        assertInvalid(SearchRequest.newBuilder().setCondominiumId(CONDOMINIUM).setText("  ").build(), "Texto");
        assertInvalid(SearchRequest.newBuilder().setCondominiumId("x").setText("a").build(), "condominio_id");
        assertInvalid(SearchRequest.newBuilder().setText("a").build(), "condominio_id");
        assertInvalid(SearchRequest.newBuilder().setCondominiumId(CONDOMINIUM).setText("a").setLimit(-1).build(),
                "Limite");
        assertInvalid(SearchRequest.newBuilder().setCondominiumId(CONDOMINIUM).setText("a")
                .setFilters(SearchFilters.newBuilder().setDateTo("30/09/2026")).build(), "data_fim");
        assertInvalid(SearchRequest.newBuilder().setCondominiumId(CONDOMINIUM).setText("a")
                .setEmbeddingModel("outro").build(), "outro");
    }

    private void assertInvalid(SearchRequest request, String chunk) {
        assertThatThrownBy(() -> client.search(request))
                .isInstanceOfSatisfying(StatusRuntimeException.class, e -> {
                    assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
                    assertThat(e.getStatus().getDescription()).contains(chunk);
                });
    }

    private static FoundChunk chunk(UUID file, Location local) {
        return new FoundChunk(UUID.randomUUID(), file, "a.pdf", "MINUTES", local, "texto", 0.5, "a".repeat(64));
    }
}
