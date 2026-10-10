package br.com.condominioauditoria.api.grpc.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.condominioauditoria.contracts.query.v2.CondominiumSummary;
import br.com.condominioauditoria.contracts.query.v2.QueryGrpc;
import br.com.condominioauditoria.contracts.query.v2.ListCondominiumsRequest;
import br.com.condominioauditoria.contracts.query.v2.ListCondominiumsResponse;
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

/** Every gRPC call requires a valid token with a role, and the token's user reaches the service code. */
class GrpcAuthInterceptorTest {

    private Server server;
    private ManagedChannel channel;

    /** Token "valido-gestor" has the MANAGER role; "valido-sem-perfil" has no role; any other is invalid. */
    private final JwtDecoder decoder = token -> switch (token) {
        case "valido-gestor" -> jwt("gestor", List.of("MANAGER"));
        case "valido-sem-perfil" -> jwt("visitante", List.of("offline_access"));
        default -> throw new BadJwtException("assinatura inválida");
    };

    @BeforeEach
    void setUp() throws Exception {
        var converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("preferred_username");
        converter.setJwtGrantedAuthoritiesConverter(jwt -> jwt.getClaimAsStringList("roles").stream()
                .map(p -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + p))
                .toList());
        // Test service: returns the name of the user Spring Security sees inside the call
        var service = new QueryGrpc.QueryImplBase() {
            @Override
            public void listCondominiums(ListCondominiumsRequest request,
                    StreamObserver<ListCondominiumsResponse> r) {
                String username = SecurityContextHolder.getContext().getAuthentication().getName();
                r.onNext(ListCondominiumsResponse.newBuilder()
                        .addCondominiums(CondominiumSummary.newBuilder().setName(username)).build());
                r.onCompleted();
            }
        };
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(ServerInterceptors.intercept(service, new GrpcAuthInterceptor(decoder, converter)))
                .build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    }

    @AfterEach
    void tearDown() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    void withoutTokenIsRejected() {
        assertThatThrownBy(() -> stub(null).listCondominiums(ListCondominiumsRequest.getDefaultInstance()))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED));
    }

    @Test
    void invalidTokenIsRejected() {
        assertThatThrownBy(() -> stub("Bearer falso").listCondominiums(ListCondominiumsRequest.getDefaultInstance()))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED));
    }

    @Test
    void tokenWithoutRoleIsRejected() {
        assertThatThrownBy(() -> stub("Bearer valido-sem-perfil").listCondominiums(ListCondominiumsRequest.getDefaultInstance()))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED));
    }

    @Test
    void validTokenReachesServiceWithUser() {
        var response = stub("Bearer valido-gestor").listCondominiums(ListCondominiumsRequest.getDefaultInstance());
        assertThat(response.getCondominiums(0).getName()).isEqualTo("gestor");
        // And it does not leak to the caller's thread
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private QueryGrpc.QueryBlockingStub stub(String authorization) {
        var stub = QueryGrpc.newBlockingStub(channel);
        if (authorization == null) {
            return stub;
        }
        var headers = new Metadata();
        headers.put(GrpcAuthInterceptor.AUTHORIZATION, authorization);
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    private static Jwt jwt(String username, List<String> roles) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "none"),
                Map.of("preferred_username", username, "roles", roles));
    }
}
