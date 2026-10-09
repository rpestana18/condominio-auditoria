package br.com.condominioauditoria.api.grpc;

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
import br.com.condominioauditoria.api.ia.ConfiguracaoIaServico;
import br.com.condominioauditoria.api.model.enums.AiMode;
import br.com.condominioauditoria.api.model.enums.FileCategory;
import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.repository.file.SourceFileRepository;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.feature.FeatureService;
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
 * rpc BuscarDocumentos de ponta a ponta dentro do processo: mcp (stub) → backend (ConsultaGrpcServico com a
 * autenticação real) → rag falso (Assistente). Confere token repassado, modo, limites, conversão, recusa de outro
 * condomínio, segunda barreira e rag fora do ar.
 */
class BuscarDocumentosTest {

    private static final UUID CONDOMINIO_A = UUID.randomUUID();
    private static final UUID CONDOMINIO_B = UUID.randomUUID();

    private final SourceFileRepository arquivos = mock(SourceFileRepository.class);
    /** Mock: exigir não lança = módulo ligado. Os testes de módulo desligado configuram a recusa. */
    private final FeatureService modulos = mock(FeatureService.class);
    private final UsageService registroUso = mock(UsageService.class);
    private final ConfiguracaoIaServico configuracaoIa = mock(ConfiguracaoIaServico.class);
    private final SourceFile ataDoA = new SourceFile(CONDOMINIO_A, FileCategory.ATA, "ata.pdf", "a/ATA/2026/x-ata.pdf",
            "a".repeat(64), 10, "application/pdf", "gestor");
    private final SourceFile poDoA = new SourceFile(CONDOMINIO_A, FileCategory.PO, "po.xlsx", "a/PO/2026/x-po.xlsx",
            "c".repeat(64), 10, "application/vnd.ms-excel", "gestor");
    private final SourceFile ataDoB = new SourceFile(CONDOMINIO_B, FileCategory.ATA, "ata-b.pdf", "b/ATA/2026/x-ata-b.pdf",
            "b".repeat(64), 10, "application/pdf", "gestor");

    /** O que o rag falso recebeu e o que ele vai devolver. */
    private final List<BuscarRequest> pedidosAoRag = new ArrayList<>();
    private final AtomicReference<String> tokenRecebidoPeloRag = new AtomicReference<>();
    private final List<Trecho> respostaDoRag = new ArrayList<>();

    private Server rag;
    private Server backend;
    private ManagedChannel canalRag;
    private ManagedChannel canalBackend;

    private final JwtDecoder decoder = token -> switch (token) {
        case "usuario-a" -> jwt(token, "usuario.a", List.of("USUARIO"), List.of(CONDOMINIO_A.toString()));
        case "admin" -> jwt(token, "admin", List.of("ADMIN"), List.of());
        default -> throw new BadJwtException("assinatura inválida");
    };

    @BeforeEach
    void subir() throws Exception {
        // Repositório: só devolve arquivos do condomínio pedido, como a consulta real
        when(arquivos.findByCondominiumIdAndIdIn(eq(CONDOMINIO_A), anyCollection())).thenAnswer(chamada -> {
            var ids = chamada.<java.util.Collection<UUID>>getArgument(1);
            return List.of(ataDoA, poDoA, ataDoB).stream()
                    .filter(a -> a.getCondominiumId().equals(CONDOMINIO_A) && ids.contains(a.getId())).toList();
        });

        var assistente = new AssistenteGrpc.AssistenteImplBase() {
            @Override
            public void buscar(BuscarRequest pedido, StreamObserver<BuscarResponse> resposta) {
                pedidosAoRag.add(pedido);
                resposta.onNext(BuscarResponse.newBuilder().addAllTrechos(respostaDoRag)
                        .setModoUsado(ModoBusca.MODO_BUSCA_HIBRIDA).build());
                resposta.onCompleted();
            }
        };
        var capturaToken = new ServerInterceptor() {
            @Override
            public <Q, R> ServerCall.Listener<Q> interceptCall(ServerCall<Q, R> chamada, Metadata cabecalhos,
                    ServerCallHandler<Q, R> proximo) {
                tokenRecebidoPeloRag.set(cabecalhos.get(AutenticacaoGrpc.AUTORIZACAO));
                return proximo.startCall(chamada, cabecalhos);
            }
        };
        String nomeRag = InProcessServerBuilder.generateName();
        rag = InProcessServerBuilder.forName(nomeRag)
                .addService(ServerInterceptors.intercept(assistente, capturaToken)).build().start();
        canalRag = InProcessChannelBuilder.forName(nomeRag).build();

        var propriedades = new ApiProperties(null, null, null, new ApiProperties.RagProperties("rag:9091", 5));
        var acesso = new CondominiumAccess();
        embeddings(AiMode.LOCAL, "bge-m3");
        var busca = new BuscaDocumentos(acesso, arquivos, new ClienteAssistente(canalRag, propriedades), modulos,
                registroUso, configuracaoIa);
        var consulta = new ConsultaGrpcServico(acesso, null, arquivos, null, null, null, null, busca);

        var conversor = new JwtAuthenticationConverter();
        conversor.setPrincipalClaimName("preferred_username");
        conversor.setJwtGrantedAuthoritiesConverter(jwt -> jwt.getClaimAsStringList("perfis").stream()
                .map(p -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + p)).toList());
        String nomeBackend = InProcessServerBuilder.generateName();
        backend = InProcessServerBuilder.forName(nomeBackend)
                .addService(ServerInterceptors.intercept(consulta, new AutenticacaoGrpc(decoder, conversor)))
                .build().start();
        canalBackend = InProcessChannelBuilder.forName(nomeBackend).build();
    }

    @AfterEach
    void descer() {
        canalBackend.shutdownNow();
        backend.shutdownNow();
        canalRag.shutdownNow();
        rag.shutdownNow();
    }

    @Test
    void repassaAoRagComOTokenEConverteOsTrechos() {
        respostaDoRag.add(trecho(ataDoA, Localizacao.newBuilder().setPagina(LocalPagina.newBuilder().setPagina(3)).build(),
                0.9));
        respostaDoRag.add(trecho(poDoA, Localizacao.newBuilder().setPlanilha(LocalPlanilha.newBuilder()
                .setAba("Previsto").setLinhaInicio(10).setLinhaFim(14)).build(), 0.5));

        BuscarDocumentosResponse resposta = stub("usuario-a").buscarDocumentos(pedido(CONDOMINIO_A, "  multa  ")
                .setFiltros(FiltrosDocumentos.newBuilder().addCategorias("ata").setDataInicio("2026-01-01"))
                .build());

        assertThat(tokenRecebidoPeloRag.get()).isEqualTo("Bearer usuario-a");
        BuscarRequest noRag = pedidosAoRag.getFirst();
        assertThat(noRag.getCondominioId()).isEqualTo(CONDOMINIO_A.toString());
        assertThat(noRag.getTexto()).isEqualTo("multa");
        assertThat(noRag.getModo()).isEqualTo(ModoBusca.MODO_BUSCA_HIBRIDA);
        assertThat(noRag.getLimite()).isEqualTo(10);
        assertThat(noRag.getFiltros().getCategoriasList()).containsExactly("ATA");
        assertThat(noRag.getFiltros().getDataInicio()).isEqualTo("2026-01-01");

        assertThat(resposta.getModoUsado()).isEqualTo(ModoBuscaDocumentos.MODO_BUSCA_DOCUMENTOS_HIBRIDA);
        assertThat(resposta.getTrechosList()).hasSize(2);
        var primeiro = resposta.getTrechos(0);
        assertThat(primeiro.getArquivoId()).isEqualTo(ataDoA.getId().toString());
        assertThat(primeiro.getNomeArquivo()).isEqualTo("ata.pdf");
        assertThat(primeiro.getSha256()).isEqualTo(ataDoA.getSha256());
        assertThat(primeiro.getTexto()).isEqualTo("texto de ata.pdf");
        assertThat(primeiro.getLocalizacao().getTipoCase()).isEqualTo(LocalizacaoTrecho.TipoCase.PAGINA);
        assertThat(primeiro.getLocalizacao().getPagina().getPagina()).isEqualTo(3);
        var segundo = resposta.getTrechos(1).getLocalizacao().getPlanilha();
        assertThat(segundo.getAba()).isEqualTo("Previsto");
        assertThat(segundo.getLinhaInicio()).isEqualTo(10);
        assertThat(segundo.getLinhaFim()).isEqualTo(14);
    }

    @Test
    void moduloDesligadoEhFailedPreconditionSemChamarORagNemRegistrarUso() {
        doThrow(new FeatureNotEnabledException(FeatureService.ASSISTANT, "Assistente"))
                .when(modulos).require(CONDOMINIO_A, FeatureService.ASSISTANT);

        assertThatThrownBy(() -> stub("usuario-a").buscarDocumentos(pedido(CONDOMINIO_A, "multa").build()))
                .isInstanceOfSatisfying(StatusRuntimeException.class, e -> {
                    assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
                    assertThat(e.getStatus().getDescription())
                            .isEqualTo("Módulo Assistente não contratado para este condomínio.");
                });
        assertThat(pedidosAoRag).isEmpty();
        verifyNoInteractions(registroUso);
    }

    @Test
    void semAcessoAoCondominioEhRecusadoAntesDeOlharOModulo() {
        assertThatThrownBy(() -> stub("usuario-a").buscarDocumentos(pedido(CONDOMINIO_B, "multa").build()))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED));
        verifyNoInteractions(modulos);
    }

    @Test
    void cadaBuscaRespondidaRegistraUmaChamadaMcpComOUsuario() {
        respostaDoRag.add(trecho(ataDoA, Localizacao.newBuilder().setPagina(LocalPagina.newBuilder().setPagina(1)).build(),
                0.9));

        stub("usuario-a").buscarDocumentos(pedido(CONDOMINIO_A, "multa").build());
        stub("usuario-a").buscarDocumentos(pedido(CONDOMINIO_A, "portão").build());

        verify(registroUso, times(2)).recordMcpCall(CONDOMINIO_A, "usuario.a", true);
    }

    @Test
    void buscaQueFalhaNoRagNaoRegistraUso() {
        rag.shutdownNow();

        assertThatThrownBy(() -> stub("usuario-a").buscarDocumentos(pedido(CONDOMINIO_A, "multa").build()))
                .isInstanceOf(StatusRuntimeException.class);
        verifyNoInteractions(registroUso);
    }

    @Test
    void segundaBarreiraDescartaTrechoDeArquivoDeOutroCondominioOuDesconhecido() {
        var pagina = Localizacao.newBuilder().setPagina(LocalPagina.newBuilder().setPagina(1)).build();
        respostaDoRag.add(trecho(ataDoB, pagina, 0.99));                  // arquivo de outro condomínio
        respostaDoRag.add(trecho(ataDoA, pagina, 0.8));                   // ok
        respostaDoRag.add(Trecho.newBuilder().setTrechoId("x").setArquivoId(UUID.randomUUID().toString())
                .setLocalizacao(pagina).build());                          // arquivo que não existe no backend
        respostaDoRag.add(Trecho.newBuilder().setTrechoId("y").setArquivoId("nao-e-uuid").build());

        var resposta = stub("usuario-a").buscarDocumentos(pedido(CONDOMINIO_A, "multa").build());

        assertThat(resposta.getTrechosList()).extracting(t -> t.getArquivoId())
                .containsExactly(ataDoA.getId().toString());
    }

    @Test
    void outroCondominioEhRecusadoSemChamarORag() {
        assertThatThrownBy(() -> stub("usuario-a").buscarDocumentos(pedido(CONDOMINIO_B, "multa").build()))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED));
        assertThat(pedidosAoRag).isEmpty();
    }

    @Test
    void semTokenEhRecusado() {
        assertThatThrownBy(() -> ConsultaGrpc.newBlockingStub(canalBackend)
                .buscarDocumentos(pedido(CONDOMINIO_A, "multa").build()))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED));
        assertThat(pedidosAoRag).isEmpty();
    }

    @Test
    void textoVazioEhInvalido() {
        assertThatThrownBy(() -> stub("usuario-a").buscarDocumentos(pedido(CONDOMINIO_A, "   ").build()))
                .isInstanceOfSatisfying(StatusRuntimeException.class, e -> {
                    assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
                    assertThat(e.getStatus().getDescription()).contains("texto");
                });
        assertThat(pedidosAoRag).isEmpty();
    }

    @Test
    void limiteNegativoEDataForaDoFormatoSaoInvalidos() {
        assertThatThrownBy(() -> stub("usuario-a").buscarDocumentos(pedido(CONDOMINIO_A, "multa").setLimite(-1).build()))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT));
        assertThatThrownBy(() -> stub("usuario-a").buscarDocumentos(pedido(CONDOMINIO_A, "multa")
                .setFiltros(FiltrosDocumentos.newBuilder().setDataFim("30/09/2026")).build()))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT));
        assertThat(pedidosAoRag).isEmpty();
    }

    @Test
    void embeddingsDesligadosBuscamSoPorPalavraSemModelo() {
        embeddings(AiMode.DESLIGADO, null);

        stub("usuario-a").buscarDocumentos(pedido(CONDOMINIO_A, "multa").build());

        assertThat(pedidosAoRag.getFirst().getModo()).isEqualTo(ModoBusca.MODO_BUSCA_PALAVRA);
        assertThat(pedidosAoRag.getFirst().getModeloEmbeddings()).isEmpty();
    }

    @Test
    void embeddingsLocaisBuscamHibridoComOModeloConfigurado() {
        stub("usuario-a").buscarDocumentos(pedido(CONDOMINIO_A, "multa").build());

        assertThat(pedidosAoRag.getFirst().getModo()).isEqualTo(ModoBusca.MODO_BUSCA_HIBRIDA);
        assertThat(pedidosAoRag.getFirst().getModeloEmbeddings()).isEqualTo("bge-m3");
    }

    private void embeddings(AiMode modo, String modelo) {
        var respostas = new ConfiguracaoIaServico.Respostas(null, AiMode.MCP_EXTERNO, null, null, null, null);
        when(configuracaoIa.ler(any())).thenReturn(new ConfiguracaoIaServico.Efetiva(AiMode.MCP_EXTERNO, respostas,
                new ConfiguracaoIaServico.Embeddings(modo, modo == AiMode.LOCAL ? "ollama-local" : null, modelo), null,
                null));
    }

    @Test
    void limiteAcimaDoMaximoEhReduzido() {
        stub("admin").buscarDocumentos(pedido(CONDOMINIO_A, "multa").setLimite(80).build());
        assertThat(pedidosAoRag.getFirst().getLimite()).isEqualTo(50);
    }

    @Test
    void ragForaDoArEhUnavailableComMensagemLegivel() {
        rag.shutdownNow();

        assertThatThrownBy(() -> stub("usuario-a").buscarDocumentos(pedido(CONDOMINIO_A, "multa").build()))
                .isInstanceOfSatisfying(StatusRuntimeException.class, e -> {
                    assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE);
                    assertThat(e.getStatus().getDescription()).contains("Busca nos documentos indisponível");
                });
    }

    private ConsultaGrpc.ConsultaBlockingStub stub(String token) {
        var cabecalhos = new Metadata();
        cabecalhos.put(AutenticacaoGrpc.AUTORIZACAO, "Bearer " + token);
        return ConsultaGrpc.newBlockingStub(canalBackend)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(cabecalhos));
    }

    private static BuscarDocumentosRequest.Builder pedido(UUID condominioId, String texto) {
        return BuscarDocumentosRequest.newBuilder().setCondominioId(condominioId.toString()).setTexto(texto);
    }

    private static Trecho trecho(SourceFile arquivo, Localizacao localizacao, double pontuacao) {
        return Trecho.newBuilder()
                .setTrechoId(UUID.randomUUID().toString())
                .setArquivoId(arquivo.getId().toString())
                .setNomeArquivo(arquivo.getOriginalName())
                .setCategoria(arquivo.getCategory().name())
                .setLocalizacao(localizacao)
                .setTexto("texto de " + arquivo.getOriginalName())
                .setPontuacao(pontuacao)
                .setSha256(arquivo.getSha256())
                .build();
    }

    private static Jwt jwt(String token, String usuario, List<String> perfis, List<String> condominios) {
        return new Jwt(token, Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "none"),
                Map.of("preferred_username", usuario, "perfis", perfis, "condominios", condominios));
    }
}
