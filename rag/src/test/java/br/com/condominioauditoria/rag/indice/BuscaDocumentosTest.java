package br.com.condominioauditoria.rag.indice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.rag.indice.RepositorioIndice.Achado;
import br.com.condominioauditoria.rag.indice.RepositorioIndice.FiltrosBusca;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Regras da busca sem banco: limite, fusão e queda para a busca por palavra quando o Ollama está fora. */
class BuscaDocumentosTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final FiltrosBusca FILTROS = new FiltrosBusca(UUID.randomUUID(), null, null, null, null);

    private final RepositorioIndice repositorio = mock(RepositorioIndice.class);
    private final GeradorEmbeddings embeddings = mock(GeradorEmbeddings.class);
    private final BuscaDocumentos busca = new BuscaDocumentos(repositorio, embeddings);

    @Test
    @SuppressWarnings("unchecked")
    void hibridaFundeAsDuasListas() {
        when(embeddings.gerar(anyString())).thenReturn(new float[1024]);
        when(embeddings.modelo()).thenReturn("bge-m3");
        when(repositorio.buscarPorPalavra(FILTROS, "multa", 50)).thenReturn(List.of(new Achado(A, 0.9),
                new Achado(B, 0.5)));
        when(repositorio.buscarPorVetor(eq(FILTROS), any(), eq("bge-m3"), eq(50), eq(null))).thenReturn(List.of(C, B));
        when(repositorio.carregar(any())).thenAnswer(i -> ((List<UUID>) i.getArgument(0)).stream()
                .map(BuscaDocumentosTest::trecho).toList());

        var resultado = busca.buscar(FILTROS, "multa", BuscaDocumentos.Modo.HIBRIDA, 0);

        assertThat(resultado.modoUsado()).isEqualTo(BuscaDocumentos.Modo.HIBRIDA);
        // B = 1/62 + 1/62 > A = 1/61 > C = 1/61 (empate A x C pela posição 1 nos dois; vale o id)
        assertThat(resultado.trechos()).extracting(TrechoEncontrado::trechoId).containsExactly(B, A, C);
        assertThat(resultado.trechos().getFirst().pontuacao()).isEqualTo(2.0 / 62);
    }

    @Test
    void ollamaForaCaiParaPalavraEAvisa() {
        when(embeddings.gerar(anyString())).thenThrow(new EmbeddingsIndisponiveisException("fora", null));
        when(repositorio.buscarPorPalavra(FILTROS, "multa", 10)).thenReturn(List.of(new Achado(A, 0.7)));
        when(repositorio.carregar(List.of(A))).thenReturn(List.of(trecho(A)));

        var resultado = busca.buscar(FILTROS, "multa", BuscaDocumentos.Modo.HIBRIDA, 0);

        assertThat(resultado.modoUsado()).isEqualTo(BuscaDocumentos.Modo.PALAVRA);
        assertThat(resultado.trechos()).singleElement().satisfies(t -> assertThat(t.pontuacao()).isEqualTo(0.7));
        verify(repositorio, never()).buscarPorVetor(any(), any(), anyString(), anyInt(), any());
    }

    @Test
    void palavraNaoChamaOllamaELimiteVaiAte50() {
        when(repositorio.buscarPorPalavra(any(), anyString(), anyInt())).thenReturn(List.of());
        when(repositorio.carregar(any())).thenReturn(List.of());

        busca.buscar(FILTROS, "ata", BuscaDocumentos.Modo.PALAVRA, 500);

        ArgumentCaptor<Integer> limite = ArgumentCaptor.forClass(Integer.class);
        verify(repositorio).buscarPorPalavra(eq(FILTROS), eq("ata"), limite.capture());
        assertThat(limite.getValue()).isEqualTo(50);
        verify(embeddings, never()).gerar(anyString());
    }

    @Test
    void exclusaoEFraseVaoParaOLadoVetorial() {
        when(embeddings.gerar(anyString())).thenReturn(new float[1024]);
        when(embeddings.modelo()).thenReturn("bge-m3");
        when(repositorio.buscarPorPalavra(any(), anyString(), anyInt())).thenReturn(List.of());
        when(repositorio.buscarPorVetor(any(), any(), anyString(), anyInt(), any())).thenReturn(List.of());
        when(repositorio.carregar(any())).thenReturn(List.of());

        busca.buscar(FILTROS, "\"folha de pagamento\" salário -transporte", BuscaDocumentos.Modo.HIBRIDA, 0);

        verify(repositorio).buscarPorVetor(eq(FILTROS), any(), eq("bge-m3"), eq(50),
                eq("\"folha de pagamento\" -transporte"));
    }

    private static TrechoEncontrado trecho(UUID id) {
        return new TrechoEncontrado(id, UUID.randomUUID(), "a.pdf", "ATA", new Localizacao.Pagina(1), "texto", 0,
                "a".repeat(64));
    }
}
