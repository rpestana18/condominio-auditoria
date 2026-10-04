package br.com.condominioauditoria.mcp.ferramentas;

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
import br.com.condominioauditoria.mcp.config.McpConfig;
import br.com.condominioauditoria.mcp.config.PropriedadesMcp;
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

/** Ferramenta buscar_documentos contra um backend falso em memória (gRPC in-process). */
class BuscarDocumentosFerramentaTest {

    private static final String CONDOMINIO = "6f1d2c1e-3b4a-4c8e-9a51-2815a0000001";
    private static final String TOKEN = "Bearer token-do-gestor";
    private static final McpTransportContext CONTEXTO =
            McpTransportContext.create(Map.of(McpConfig.AUTORIZACAO, TOKEN));

    private final AtomicReference<BuscarDocumentosRequest> pedidoRecebido = new AtomicReference<>();
    private final AtomicReference<String> tokenRecebido = new AtomicReference<>();
    private final AtomicReference<Status> erroADevolver = new AtomicReference<>();
    private Server servidor;
    private ManagedChannel canal;
    private FerramentasCondominio ferramentas;

    @BeforeEach
    void subir() throws Exception {
        String nome = InProcessServerBuilder.generateName();
        var backendFalso = new ConsultaGrpc.ConsultaImplBase() {
            @Override
            public void buscarDocumentos(BuscarDocumentosRequest pedido,
                    StreamObserver<BuscarDocumentosResponse> resposta) {
                pedidoRecebido.set(pedido);
                if (erroADevolver.get() != null) {
                    resposta.onError(erroADevolver.get().asRuntimeException());
                    return;
                }
                resposta.onNext(BuscarDocumentosResponse.newBuilder()
                        .setModoUsado(ModoBuscaDocumentos.MODO_BUSCA_DOCUMENTOS_PALAVRA)
                        .addTrechos(trecho("ata-2025.pdf", "ATA", LocalizacaoTrecho.newBuilder()
                                .setPagina(LocalPagina.newBuilder().setPagina(3)).build()))
                        .addTrechos(trecho("previsao.xlsx", "PO", LocalizacaoTrecho.newBuilder()
                                .setPlanilha(LocalPlanilha.newBuilder().setAba("Plan1").setLinhaInicio(2)
                                        .setLinhaFim(31)).build()))
                        .addTrechos(trecho("contrato.docx", "CONTRATO", LocalizacaoTrecho.newBuilder()
                                .setParagrafos(LocalParagrafos.newBuilder().setParagrafoInicio(4)
                                        .setParagrafoFim(7)).build()))
                        .build());
                resposta.onCompleted();
            }
        };
        ServerInterceptor guardaToken = new ServerInterceptor() {
            @Override
            public <Q, R> ServerCall.Listener<Q> interceptCall(ServerCall<Q, R> chamada, Metadata cabecalhos,
                    ServerCallHandler<Q, R> proximo) {
                tokenRecebido.set(cabecalhos.get(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)));
                return proximo.startCall(chamada, cabecalhos);
            }
        };
        servidor = InProcessServerBuilder.forName(nome).directExecutor()
                .addService(ServerInterceptors.intercept(backendFalso, guardaToken)).build().start();
        canal = InProcessChannelBuilder.forName(nome).directExecutor().build();
        var propriedades = new PropriedadesMcp(new PropriedadesMcp.Backend("em-memoria", 5));
        ferramentas = new FerramentasCondominio(new ClienteBackend(canal, propriedades));
    }

    @AfterEach
    void descer() {
        canal.shutdownNow();
        servidor.shutdownNow();
    }

    @Test
    void repassaFiltrosETokenEDevolveLocalizacaoLegivel() {
        var resultado = ferramentas.buscarDocumentos(CONTEXTO, CONDOMINIO, " \"reajuste da taxa\" -2023 ",
                List.of("ATA", " ", "CONTRATO"), "2025-01-01", "2025-12-31", null, null);

        assertThat(tokenRecebido.get()).isEqualTo(TOKEN);
        var pedido = pedidoRecebido.get();
        assertThat(pedido.getCondominioId()).isEqualTo(CONDOMINIO);
        assertThat(pedido.getTexto()).isEqualTo("\"reajuste da taxa\" -2023");
        assertThat(pedido.getLimite()).isEqualTo(10);
        assertThat(pedido.getFiltros().getCategoriasList()).containsExactly("ATA", "CONTRATO");
        assertThat(pedido.getFiltros().getDataInicio()).isEqualTo("2025-01-01");
        assertThat(pedido.getFiltros().getDataFim()).isEqualTo("2025-12-31");
        assertThat(pedido.getFiltros().getArquivoIdsList()).isEmpty();

        assertThat(resultado.modoUsado()).isEqualTo("PALAVRA");
        assertThat(resultado.total()).isEqualTo(3);
        assertThat(resultado.trechos()).extracting(FerramentasCondominio.TrechoEncontrado::localizacao)
                .containsExactly("página 3", "aba Plan1, linhas 2–31", "parágrafos 4–7");
        assertThat(resultado.trechos().getFirst().pagina()).isEqualTo(3);
        assertThat(resultado.trechos().get(1).pagina()).isNull();
        assertThat(resultado.trechos().getFirst().documento()).isEqualTo("ata-2025.pdf");
        assertThat(resultado.aviso()).contains("não conferido");
    }

    @Test
    void limiteAcimaDoMaximoViraCinquenta() {
        ferramentas.buscarDocumentos(CONTEXTO, CONDOMINIO, "elevador", null, null, null, List.of("abc"), 500);

        assertThat(pedidoRecebido.get().getLimite()).isEqualTo(50);
        assertThat(pedidoRecebido.get().getFiltros().getArquivoIdsList()).containsExactly("abc");
    }

    @Test
    void textoVazioELimiteInvalidoSaoRecusadosSemChamarOBackend() {
        assertThatThrownBy(() -> ferramentas.buscarDocumentos(CONTEXTO, CONDOMINIO, "  ", null, null, null, null,
                null)).isInstanceOf(IllegalArgumentException.class).hasMessage("Informe o texto da busca");
        assertThatThrownBy(() -> ferramentas.buscarDocumentos(CONTEXTO, CONDOMINIO, "taxa", null, null, null, null,
                0)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("de 1 a 50");
        assertThat(pedidoRecebido.get()).isNull();
    }

    @Test
    void semTokenRecusa() {
        assertThatThrownBy(() -> ferramentas.buscarDocumentos(McpTransportContext.EMPTY, CONDOMINIO, "taxa", null,
                null, null, null, null)).hasMessageContaining("sem token");
    }

    @Test
    void errosDoBackendViramMensagensLegiveis() {
        erroADevolver.set(Status.FAILED_PRECONDITION
                .withDescription("módulo Assistente não contratado para este condomínio"));
        assertThatThrownBy(() -> buscar()).isInstanceOf(IllegalStateException.class)
                .hasMessage("módulo Assistente não contratado para este condomínio");

        erroADevolver.set(Status.UNAVAILABLE.withDescription("Busca nos documentos indisponível no momento"));
        assertThatThrownBy(() -> buscar()).hasMessage("Busca nos documentos indisponível no momento");

        erroADevolver.set(Status.INVALID_ARGUMENT.withDescription("Categoria desconhecida: X"));
        assertThatThrownBy(() -> buscar()).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Categoria desconhecida: X");

        erroADevolver.set(Status.UNIMPLEMENTED);
        assertThatThrownBy(() -> buscar()).hasMessage("Função indisponível nesta versão do backend");

        erroADevolver.set(Status.PERMISSION_DENIED.withDescription("Sem acesso ao condomínio"));
        assertThatThrownBy(() -> buscar()).hasMessage("Sem acesso ao condomínio");
    }

    @Test
    void localizacaoDeUmaLinhaSoESecao() {
        assertThat(FerramentasCondominio.localizacaoLegivel(LocalizacaoTrecho.newBuilder()
                .setPlanilha(LocalPlanilha.newBuilder().setAba("Resumo").setLinhaInicio(5).setLinhaFim(5)).build()))
                .isEqualTo("aba Resumo, linha 5");
        assertThat(FerramentasCondominio.localizacaoLegivel(LocalizacaoTrecho.newBuilder()
                .setParagrafos(LocalParagrafos.newBuilder().setParagrafoInicio(2).setParagrafoFim(2)
                        .setSecao("Cláusula 5")).build()))
                .isEqualTo("parágrafo 2, seção Cláusula 5");
        assertThat(FerramentasCondominio.localizacaoLegivel(LocalizacaoTrecho.getDefaultInstance()))
                .isEqualTo("localização não informada");
    }

    private void buscar() {
        ferramentas.buscarDocumentos(CONTEXTO, CONDOMINIO, "taxa", null, null, null, null, null);
    }

    private static TrechoDocumento trecho(String nome, String categoria, LocalizacaoTrecho localizacao) {
        return TrechoDocumento.newBuilder().setTrechoId("t-" + nome).setArquivoId("a-" + nome).setNomeArquivo(nome)
                .setCategoria(categoria).setLocalizacao(localizacao).setTexto("texto de " + nome).setPontuacao(1.5)
                .setSha256("abc").build();
    }
}
