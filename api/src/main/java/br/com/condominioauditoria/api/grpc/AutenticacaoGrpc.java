package br.com.condominioauditoria.api.grpc;

import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

/**
 * Mesma regra da API REST, no gRPC: só entra chamada com token Bearer válido do Keycloak e com perfil USUARIO,
 * GESTOR ou ADMIN. O usuário do token vira o usuário da chamada, então o isolamento por condomínio
 * ({@code AcessoCondominio}) funciona igual nos dois caminhos.
 */
@Component
class AutenticacaoGrpc implements ServerInterceptor {

    static final Metadata.Key<String> AUTORIZACAO = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final Set<String> PERFIS = Set.of("ROLE_USUARIO", "ROLE_GESTOR", "ROLE_ADMIN");

    private final JwtDecoder decoder;
    private final JwtAuthenticationConverter conversor;

    AutenticacaoGrpc(JwtDecoder decoder, JwtAuthenticationConverter conversor) {
        this.decoder = decoder;
        this.conversor = conversor;
    }

    @Override
    public <Q, R> ServerCall.Listener<Q> interceptCall(ServerCall<Q, R> chamada, Metadata cabecalhos,
            ServerCallHandler<Q, R> proximo) {
        String valor = cabecalhos.get(AUTORIZACAO);
        if (valor == null || !valor.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return recusar(chamada, Status.UNAUTHENTICATED.withDescription("Token ausente"));
        }
        AbstractAuthenticationToken autenticacao;
        try {
            Jwt jwt = decoder.decode(valor.substring(7).trim());
            autenticacao = conversor.convert(jwt);
        } catch (JwtException erro) {
            return recusar(chamada, Status.UNAUTHENTICATED.withDescription("Token inválido ou expirado"));
        }
        boolean temPerfil = autenticacao.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch(PERFIS::contains);
        if (!temPerfil) {
            return recusar(chamada, Status.PERMISSION_DENIED.withDescription("Usuário sem perfil no sistema"));
        }
        SecurityContext contexto = new SecurityContextImpl(autenticacao);
        // O gRPC chama os métodos em threads do pool dele: o contexto de segurança é colocado e retirado em cada passo
        return new SimpleForwardingServerCallListener<>(comContexto(contexto, () -> proximo.startCall(chamada, cabecalhos))) {
            @Override
            public void onMessage(Q mensagem) {
                comContexto(contexto, () -> {
                    super.onMessage(mensagem);
                    return null;
                });
            }

            @Override
            public void onHalfClose() {
                comContexto(contexto, () -> {
                    super.onHalfClose();
                    return null;
                });
            }

            @Override
            public void onCancel() {
                comContexto(contexto, () -> {
                    super.onCancel();
                    return null;
                });
            }

            @Override
            public void onComplete() {
                comContexto(contexto, () -> {
                    super.onComplete();
                    return null;
                });
            }

            @Override
            public void onReady() {
                comContexto(contexto, () -> {
                    super.onReady();
                    return null;
                });
            }
        };
    }

    private static <T> T comContexto(SecurityContext contexto, Supplier<T> acao) {
        SecurityContextHolder.setContext(contexto);
        try {
            return acao.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static <Q, R> ServerCall.Listener<Q> recusar(ServerCall<Q, R> chamada, Status status) {
        chamada.close(status, new Metadata());
        return new ServerCall.Listener<>() {
        };
    }
}
