package br.com.condominioauditoria.api.grpc.server;

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
 * Same rule as the REST API, in gRPC: only calls with a valid Keycloak Bearer token and the USER, MANAGER or ADMIN
 * role get in. The token's user becomes the call's user, so the per-condominium isolation ({@code CondominiumAccess})
 * works the same on both paths.
 */
@Component
public class GrpcAuthInterceptor implements ServerInterceptor {

    public static final Metadata.Key<String> AUTHORIZATION = Metadata.Key.of("authorization",
            Metadata.ASCII_STRING_MARSHALLER);
    private static final Set<String> ROLES = Set.of("ROLE_USER", "ROLE_MANAGER", "ROLE_ADMIN");

    private final JwtDecoder decoder;
    private final JwtAuthenticationConverter converter;

    public GrpcAuthInterceptor(JwtDecoder decoder, JwtAuthenticationConverter converter) {
        this.decoder = decoder;
        this.converter = converter;
    }

    @Override
    public <Q, R> ServerCall.Listener<Q> interceptCall(ServerCall<Q, R> call, Metadata headers,
            ServerCallHandler<Q, R> next) {
        String value = headers.get(AUTHORIZATION);
        if (value == null || !value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return reject(call, Status.UNAUTHENTICATED.withDescription("Token ausente"));
        }
        AbstractAuthenticationToken authentication;
        try {
            Jwt jwt = decoder.decode(value.substring(7).trim());
            authentication = converter.convert(jwt);
        } catch (JwtException error) {
            return reject(call, Status.UNAUTHENTICATED.withDescription("Token inválido ou expirado"));
        }
        boolean hasRole = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch(ROLES::contains);
        if (!hasRole) {
            return reject(call, Status.PERMISSION_DENIED.withDescription("Usuário sem perfil no sistema"));
        }
        SecurityContext context = new SecurityContextImpl(authentication);
        // gRPC calls the methods on its own pool threads: the security context is set and cleared on each step
        return new SimpleForwardingServerCallListener<>(withContext(context, () -> next.startCall(call, headers))) {
            @Override
            public void onMessage(Q message) {
                withContext(context, () -> {
                    super.onMessage(message);
                    return null;
                });
            }

            @Override
            public void onHalfClose() {
                withContext(context, () -> {
                    super.onHalfClose();
                    return null;
                });
            }

            @Override
            public void onCancel() {
                withContext(context, () -> {
                    super.onCancel();
                    return null;
                });
            }

            @Override
            public void onComplete() {
                withContext(context, () -> {
                    super.onComplete();
                    return null;
                });
            }

            @Override
            public void onReady() {
                withContext(context, () -> {
                    super.onReady();
                    return null;
                });
            }
        };
    }

    private static <T> T withContext(SecurityContext context, Supplier<T> action) {
        SecurityContextHolder.setContext(context);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static <Q, R> ServerCall.Listener<Q> reject(ServerCall<Q, R> call, Status status) {
        call.close(status, new Metadata());
        return new ServerCall.Listener<>() {
        };
    }
}
