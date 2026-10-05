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
import br.com.condominioauditoria.rag.assistente.catalogo.CatalogoProvedores;
import br.com.condominioauditoria.rag.assistente.chave.ChavesRag;
import br.com.condominioauditoria.rag.assistente.pergunta.ServicoPergunta;
import br.com.condominioauditoria.rag.indice.BuscaDocumentos;
import br.com.condominioauditoria.rag.indice.GeradorEmbeddings;
import br.com.condominioauditoria.rag.indice.Localizacao;
import br.com.condominioauditoria.rag.indice.RepositorioIndice;
import br.com.condominioauditoria.rag.indice.TrechoEncontrado;
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

/** Contrato gRPC Assistente.Buscar: validação da entrada, filtros repassados e localização de cada tipo. */
class AssistenteGrpcServicoTest {

    private static final String CONDOMINIO = "6f1d2c1e-3b4a-4c8e-9a51-2815a0000001";

    private final BuscaDocumentos busca = mock(BuscaDocumentos.class);
    private final GeradorEmbeddings embeddings = mock(GeradorEmbeddings.class);
    private Server servidor;
    private ManagedChannel canal;
    private AssistenteGrpc.AssistenteBlockingStub cliente;

    @BeforeEach
    void subir() throws Exception {
        when(embeddings.aceita(anyString())).thenAnswer(i -> {
            String m = i.getArgument(0);
            return m.isEmpty() || m.equals("bge-m3");
        });
        when(embeddings.modelo()).thenReturn("bge-m3");
        String nome = InProcessServerBuilder.generateName();
        servidor = InProcessServerBuilder.forName(nome).directExecutor()
                .addService(new AssistenteGrpcServico(busca, embeddings, mock(ServicoPergunta.class),
                        mock(CatalogoProvedores.class), mock(ChavesRag.class)))
                .build().start();
        canal = InProcessChannelBuilder.forName(nome).directExecutor().build();
        cliente = AssistenteGrpc.newBlockingStub(canal);
    }

    @AfterEach
    void descer() {
        canal.shutdownNow();
        servidor.shutdownNow();
    }

    @Test
    void devolveTrechosComLocalizacaoEModoUsado() {
        UUID arquivo = UUID.randomUUID();
        when(busca.buscar(any(), eq("multa"), eq(BuscaDocumentos.Modo.HIBRIDA), eq(0))).thenReturn(
                new BuscaDocumentos.Resultado(List.of(
                        trecho(arquivo, new Localizacao.Pagina(3)),
                        trecho(arquivo, new Localizacao.Planilha("Jan", 2, 31)),
                        trecho(arquivo, new Localizacao.Paragrafos(4, 7, ""))),
                        BuscaDocumentos.Modo.PALAVRA));

        BuscarResponse resposta = cliente.buscar(BuscarRequest.newBuilder().setCondominioId(CONDOMINIO)
                .setTexto("multa").build());

        assertThat(resposta.getModoUsado()).isEqualTo(ModoBusca.MODO_BUSCA_PALAVRA);
        assertThat(resposta.getTrechosList()).hasSize(3);
        assertThat(resposta.getTrechos(0).getLocalizacao().getPagina().getPagina()).isEqualTo(3);
        assertThat(resposta.getTrechos(1).getLocalizacao().getPlanilha().getAba()).isEqualTo("Jan");
        assertThat(resposta.getTrechos(1).getLocalizacao().getPlanilha().getLinhaFim()).isEqualTo(31);
        assertThat(resposta.getTrechos(2).getLocalizacao().getParagrafos().getParagrafoInicio()).isEqualTo(4);
        assertThat(resposta.getTrechos(0).getArquivoId()).isEqualTo(arquivo.toString());
        assertThat(resposta.getTrechos(0).getSha256()).hasSize(64);
    }

    @Test
    void filtrosVaoParaABusca() {
        UUID arquivo = UUID.randomUUID();
        when(busca.buscar(any(), anyString(), any(), anyInt()))
                .thenReturn(new BuscaDocumentos.Resultado(List.of(), BuscaDocumentos.Modo.PALAVRA));

        cliente.buscar(BuscarRequest.newBuilder().setCondominioId(CONDOMINIO).setTexto("\"fundo de reserva\"")
                .setModo(ModoBusca.MODO_BUSCA_PALAVRA).setLimite(5)
                .setFiltros(FiltrosBusca.newBuilder().addCategorias("ATA").setDataInicio("2026-01-01")
                        .addArquivoIds(arquivo.toString()))
                .build());

        ArgumentCaptor<RepositorioIndice.FiltrosBusca> filtros = ArgumentCaptor.forClass(
                RepositorioIndice.FiltrosBusca.class);
        verify(busca).buscar(filtros.capture(), eq("\"fundo de reserva\""), eq(BuscaDocumentos.Modo.PALAVRA), eq(5));
        assertThat(filtros.getValue().condominioId()).isEqualTo(UUID.fromString(CONDOMINIO));
        assertThat(filtros.getValue().categorias()).containsExactly("ATA");
        assertThat(filtros.getValue().dataInicio()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(filtros.getValue().dataFim()).isNull();
        assertThat(filtros.getValue().arquivoIds()).containsExactly(arquivo);
    }

    @Test
    void entradaInvalidaEhInvalidArgument() {
        assertInvalido(BuscarRequest.newBuilder().setCondominioId(CONDOMINIO).setTexto("  ").build(), "Texto");
        assertInvalido(BuscarRequest.newBuilder().setCondominioId("x").setTexto("a").build(), "condominio_id");
        assertInvalido(BuscarRequest.newBuilder().setTexto("a").build(), "condominio_id");
        assertInvalido(BuscarRequest.newBuilder().setCondominioId(CONDOMINIO).setTexto("a").setLimite(-1).build(),
                "Limite");
        assertInvalido(BuscarRequest.newBuilder().setCondominioId(CONDOMINIO).setTexto("a")
                .setFiltros(FiltrosBusca.newBuilder().setDataFim("30/09/2026")).build(), "data_fim");
        assertInvalido(BuscarRequest.newBuilder().setCondominioId(CONDOMINIO).setTexto("a")
                .setModeloEmbeddings("outro").build(), "outro");
    }

    private void assertInvalido(BuscarRequest pedido, String trecho) {
        assertThatThrownBy(() -> cliente.buscar(pedido))
                .isInstanceOfSatisfying(StatusRuntimeException.class, e -> {
                    assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
                    assertThat(e.getStatus().getDescription()).contains(trecho);
                });
    }

    private static TrechoEncontrado trecho(UUID arquivo, Localizacao local) {
        return new TrechoEncontrado(UUID.randomUUID(), arquivo, "a.pdf", "ATA", local, "texto", 0.5, "a".repeat(64));
    }
}
