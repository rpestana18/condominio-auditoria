package br.com.condominioauditoria.rag.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.rag.exception.EmbeddingsUnavailableException;
import br.com.condominioauditoria.rag.repository.IndexRepository;
import br.com.condominioauditoria.rag.repository.IndexRepository.Hit;
import br.com.condominioauditoria.rag.repository.IndexRepository.SearchFilters;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Search rules without a database: limit, fusion and fallback to keyword search when Ollama is down. */
class DocumentSearchTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final SearchFilters FILTERS = new SearchFilters(UUID.randomUUID(), null, null, null, null);

    private final IndexRepository repository = mock(IndexRepository.class);
    private final EmbeddingGenerator embeddings = mock(EmbeddingGenerator.class);
    private final DocumentSearch search = new DocumentSearch(repository, embeddings);

    @Test
    @SuppressWarnings("unchecked")
    void hybridFusesBothLists() {
        when(embeddings.generate(anyString())).thenReturn(new float[1024]);
        when(embeddings.model()).thenReturn("bge-m3");
        when(repository.findByKeyword(FILTERS, "multa", 50)).thenReturn(List.of(new Hit(A, 0.9),
                new Hit(B, 0.5)));
        when(repository.findByVector(eq(FILTERS), any(), eq("bge-m3"), eq(50), eq(null))).thenReturn(List.of(C, B));
        when(repository.load(any())).thenAnswer(i -> ((List<UUID>) i.getArgument(0)).stream()
                .map(DocumentSearchTest::chunk).toList());

        var result = search.search(FILTERS, "multa", DocumentSearch.Mode.HYBRID, 0);

        assertThat(result.modeUsed()).isEqualTo(DocumentSearch.Mode.HYBRID);
        // B = 1/62 + 1/62 > A = 1/61 > C = 1/61 (tie A x C by rank 1 in both; the id decides)
        assertThat(result.chunks()).extracting(FoundChunk::chunkId).containsExactly(B, A, C);
        assertThat(result.chunks().getFirst().score()).isEqualTo(2.0 / 62);
    }

    @Test
    void ollamaDownFallsBackToKeywordAndWarns() {
        when(embeddings.generate(anyString())).thenThrow(new EmbeddingsUnavailableException("fora", null));
        when(repository.findByKeyword(FILTERS, "multa", 10)).thenReturn(List.of(new Hit(A, 0.7)));
        when(repository.load(List.of(A))).thenReturn(List.of(chunk(A)));

        var result = search.search(FILTERS, "multa", DocumentSearch.Mode.HYBRID, 0);

        assertThat(result.modeUsed()).isEqualTo(DocumentSearch.Mode.KEYWORD);
        assertThat(result.chunks()).singleElement().satisfies(t -> assertThat(t.score()).isEqualTo(0.7));
        verify(repository, never()).findByVector(any(), any(), anyString(), anyInt(), any());
    }

    @Test
    void keywordDoesNotCallOllamaAndLimitGoesUpTo50() {
        when(repository.findByKeyword(any(), anyString(), anyInt())).thenReturn(List.of());
        when(repository.load(any())).thenReturn(List.of());

        search.search(FILTERS, "ata", DocumentSearch.Mode.KEYWORD, 500);

        ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
        verify(repository).findByKeyword(eq(FILTERS), eq("ata"), limit.capture());
        assertThat(limit.getValue()).isEqualTo(50);
        verify(embeddings, never()).generate(anyString());
    }

    @Test
    void exclusionAndPhraseGoToVectorSide() {
        when(embeddings.generate(anyString())).thenReturn(new float[1024]);
        when(embeddings.model()).thenReturn("bge-m3");
        when(repository.findByKeyword(any(), anyString(), anyInt())).thenReturn(List.of());
        when(repository.findByVector(any(), any(), anyString(), anyInt(), any())).thenReturn(List.of());
        when(repository.load(any())).thenReturn(List.of());

        search.search(FILTERS, "\"folha de pagamento\" salário -transporte", DocumentSearch.Mode.HYBRID, 0);

        verify(repository).findByVector(eq(FILTERS), any(), eq("bge-m3"), eq(50),
                eq("\"folha de pagamento\" -transporte"));
    }

    private static FoundChunk chunk(UUID id) {
        return new FoundChunk(id, UUID.randomUUID(), "a.pdf", "MINUTES", new Location.Page(1), "texto", 0,
                "a".repeat(64));
    }
}
