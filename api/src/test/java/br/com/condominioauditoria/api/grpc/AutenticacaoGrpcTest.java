package br.com.condominioauditoria.api.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.condominioauditoria.contratos.consulta.v1.CondominioResumo;
import br.com.condominioauditoria.contratos.consulta.v1.ConsultaGrpc;
import br.com.condominioauditoria.contratos.consulta.v1.ListarCondominiosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ListarCondominiosResponse;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/** Toda chamada gRPC exige token válido com perfil, e o usuário do token chega ao código do serviço. */
class AutenticacaoGrpcTest {

    private Server servidor;
    private ManagedChannel canal;

    /** Token "valido-gestor" tem perfil GESTOR; "valido-sem-perfil" não tem perfil; qualquer outro é inválido. */
    private final JwtDecoder decoder = token -> switch (token) {
        case "valido-gestor" -> jwt("gestor", List.of("GESTOR"));
        case "valido-sem-perfil" -> jwt("visitante", List.of("offline_access"));
        default -> throw new BadJwtException("assinatura inválida");
    };

    @BeforeEach
    void subir() throws Exception {
        var conversor = new JwtAuthenticationConverter();
        conversor.setPrincipalClaimName("preferred_username");
        conversor.setJwtGrantedAuthoritiesConverter(jwt -> jwt.getClaimAsStringList("perfis").stream()
                .map(p -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + p))
                .toList());
        // Serviço de teste: devolve o nome do usuário que o Spring Security enxerga dentro da chamada
        var servico = new ConsultaGrpc.ConsultaImplBase() {
            @Override
            public void listarCondominios(ListarCondominiosRequest pedido, StreamObserver<ListarCondominiosResponse> r) {
                String usuario = SecurityContextHolder.getContext().getAuthentication().getName();
                r.onNext(ListarCondominiosResponse.newBuilder()
                        .addCondominios(CondominioResumo.newBuilder().setNome(usuario)).build());
                r.onCompleted();
            }
        };
        String nome = InProcessServerBuilder.generateName();
        servidor = InProcessServerBuilder.forName(nome).directExecutor()
                .addService(ServerInterceptors.intercept(servico, new AutenticacaoGrpc(decoder, conversor)))
                .build().start();
        canal = InProcessChannelBuilder.forName(nome).directExecutor().build();
    }

    @AfterEach
    void descer() {
        canal.shutdownNow();
        servidor.shutdownNow();
    }

    @Test
    void semTokenEhRecusado() {
        assertThatThrownBy(() -> stub(null).listarCondominios(ListarCondominiosRequest.getDefaultInstance()))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED));
    }

    @Test
    void tokenInvalidoEhRecusado() {
        assertThatThrownBy(() -> stub("Bearer falso").listarCondominios(ListarCondominiosRequest.getDefaultInstance()))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED));
    }

    @Test
    void tokenSemPerfilEhRecusado() {
        assertThatThrownBy(() -> stub("Bearer valido-sem-perfil").listarCondominios(ListarCondominiosRequest.getDefaultInstance()))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED));
    }

    @Test
    void tokenValidoChegaAoServicoComOUsuario() {
        var resposta = stub("Bearer valido-gestor").listarCondominios(ListarCondominiosRequest.getDefaultInstance());
        assertThat(resposta.getCondominios(0).getNome()).isEqualTo("gestor");
        // E não vaza para a thread de quem chamou
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private ConsultaGrpc.ConsultaBlockingStub stub(String autorizacao) {
        var stub = ConsultaGrpc.newBlockingStub(canal);
        if (autorizacao == null) {
            return stub;
        }
        var cabecalhos = new Metadata();
        cabecalhos.put(AutenticacaoGrpc.AUTORIZACAO, autorizacao);
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(cabecalhos));
    }

    private static Jwt jwt(String usuario, List<String> perfis) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "none"),
                Map.of("preferred_username", usuario, "perfis", perfis));
    }
}
