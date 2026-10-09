package br.com.condominioauditoria.api.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.api.config.properties.ApiProperties;
import br.com.condominioauditoria.api.model.condominium.Condominium;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.contratos.assistente.v1.AssistenteGrpc;
import br.com.condominioauditoria.contratos.assistente.v1.DadoGravado;
import br.com.condominioauditoria.contratos.assistente.v1.LinhaDado;
import br.com.condominioauditoria.contratos.assistente.v1.PerguntarEvento;
import br.com.condominioauditoria.contratos.assistente.v1.PerguntarRequest;
import br.com.condominioauditoria.contratos.assistente.v1.RespostaPergunta;
import br.com.condominioauditoria.contratos.assistente.v1.SituacaoResposta;
import br.com.condominioauditoria.contratos.consulta.v1.ConsultaGrpc;
import br.com.condominioauditoria.contratos.consulta.v1.ListarCondominiosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ListarCondominiosResponse;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
 * Ida e volta da pergunta (ADR 0003, Decisão 5.2): uma thread "da API" chama o rag (Perguntar) e fica esperando;
 * durante a pergunta, o rag chama a Consulta do backend pelo servidor gRPC real (porta de verdade, grupo de threads
 * próprio) com o token do usuário recebido no metadado. Confere que o backend aceita o token repassado pelo rag, aplica
 * o acesso do usuário (só o condomínio A) e que nada trava com uma única thread de API.
 */
class IdaEVoltaPerguntaTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    private ServidorGrpc backend;
    private Server rag;
    private ManagedChannel canalRag;
    private ManagedChannel canalRagParaBackend;
    private final AtomicReference<String> threadDaConsulta = new AtomicReference<>();

    private final JwtDecoder decoder = token -> switch (token) {
        case "usuario-a" -> new Jwt(token, Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "none"),
                Map.of("preferred_username", "usuario.a", "perfis", List.of("USUARIO"), "condominios",
                        List.of(A.toString())));
        default -> throw new BadJwtException("assinatura inválida");
    };

    @BeforeEach
    void subir() throws Exception {
        CondominiumRepository condominios = mock(CondominiumRepository.class);
        Condominium ca = mock(Condominium.class);
        when(ca.getId()).thenReturn(A);
        when(ca.getName()).thenReturn("Condomínio A");
        Condominium cb = mock(Condominium.class);
        when(cb.getId()).thenReturn(B);
        when(cb.getName()).thenReturn("Condomínio B");
        when(condominios.findAll()).thenAnswer(i -> {
            threadDaConsulta.set(Thread.currentThread().getName());
            return List.of(ca, cb);
        });
        var acesso = new CondominiumAccess();
        var consulta = new ConsultaGrpcServico(acesso, condominios, null, null, null, null, null, null);
        var conversor = new JwtAuthenticationConverter();
        conversor.setPrincipalClaimName("preferred_username");
        conversor.setJwtGrantedAuthoritiesConverter(jwt -> jwt.getClaimAsStringList("perfis").stream()
                .map(p -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + p)).toList());
        backend = new ServidorGrpc(consulta, new AutenticacaoGrpc(decoder, conversor),
                new ApiProperties(null, null, new ApiProperties.GrpcProperties(0), null));
        backend.start();
        canalRagParaBackend = Grpc.newChannelBuilder("localhost:" + backend.porta(), InsecureChannelCredentials.create())
                .build();

        // rag falso: no meio da pergunta, chama a Consulta do backend com o token que recebeu
        var tokenRecebido = new AtomicReference<String>();
        var assistente = new AssistenteGrpc.AssistenteImplBase() {
            @Override
            public void perguntar(PerguntarRequest pedido, StreamObserver<PerguntarEvento> resposta) {
                var cabecalhos = new Metadata();
                cabecalhos.put(AutenticacaoGrpc.AUTORIZACAO, tokenRecebido.get());
                ListarCondominiosResponse lista = ConsultaGrpc.newBlockingStub(canalRagParaBackend)
                        .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(cabecalhos))
                        .withDeadlineAfter(10, TimeUnit.SECONDS)
                        .listarCondominios(ListarCondominiosRequest.getDefaultInstance());
                var dado = DadoGravado.newBuilder().setChamadaId("c1").setConsulta("listar_condominios");
                lista.getCondominiosList().forEach(c -> dado.addLinhas(LinhaDado.newBuilder().setRotulo("Condomínio")
                        .setValor(c.getNome())));
                resposta.onNext(PerguntarEvento.newBuilder().setResposta(RespostaPergunta.newBuilder()
                        .setSituacao(SituacaoResposta.SITUACAO_RESPOSTA_RESPONDIDA).addNosDadosGravados(dado)).build());
                resposta.onCompleted();
            }
        };
        var capturaToken = new ServerInterceptor() {
            @Override
            public <Q, R> ServerCall.Listener<Q> interceptCall(ServerCall<Q, R> chamada, Metadata cabecalhos,
                    ServerCallHandler<Q, R> proximo) {
                tokenRecebido.set(cabecalhos.get(AutenticacaoGrpc.AUTORIZACAO));
                return proximo.startCall(chamada, cabecalhos);
            }
        };
        String nome = InProcessServerBuilder.generateName();
        rag = InProcessServerBuilder.forName(nome).addService(ServerInterceptors.intercept(assistente, capturaToken))
                .build().start();
        canalRag = InProcessChannelBuilder.forName(nome).build();
    }

    @AfterEach
    void descer() {
        canalRag.shutdownNow();
        rag.shutdownNow();
        canalRagParaBackend.shutdownNow();
        backend.stop();
    }

    @Test
    void ragChamaAConsultaDeVoltaComOTokenDoUsuarioSemTravarAThreadDaApi() throws Exception {
        var cliente = new ClienteAssistente(canalRag, new ApiProperties(null, null, null,
                new ApiProperties.RagProperties("rag:9091", 5, 20, 300)));
        ExecutorService threadDaApi = Executors.newSingleThreadExecutor(r -> new Thread(r, "http-nio-teste"));
        try {
            RespostaPergunta resposta = threadDaApi.submit(() -> cliente.perguntar(PerguntarRequest.newBuilder()
                    .setCondominioId(A.toString()).setPergunta("quais condomínios?").build(), "Bearer usuario-a"))
                    .get(20, TimeUnit.SECONDS);

            assertThat(resposta.getNosDadosGravados(0).getLinhasList()).extracting(LinhaDado::getValor)
                    .containsExactly("Condomínio A"); // o B não aparece: acesso do usuário aplicado
            assertThat(threadDaConsulta.get()).startsWith("grpc-servidor-");
        } finally {
            threadDaApi.shutdownNow();
        }
    }
}
