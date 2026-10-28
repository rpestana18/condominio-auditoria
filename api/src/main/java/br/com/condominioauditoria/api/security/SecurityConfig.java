package br.com.condominioauditoria.api.security;

import jakarta.servlet.DispatcherType;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The api is a "resource server": it only accepts a valid Bearer token issued by Keycloak. Roles come from
 * realm_access.roles (USER, MANAGER, ADMIN) and become ROLE_USER etc.
 */
@Configuration
@EnableMethodSecurity
class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter roleConverter) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // API without a session cookie: the token goes in the header
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // Internal error dispatch (404, 400, 409 from ResponseStatusException): without this the server
                        // forwards to /error, denyAll refuses it and every error arrives as 403. A direct call to
                        // /error is REQUEST, not ERROR, and is still refused.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Spring's error page: without this, 400 and 404 handled by Spring itself become an empty 403
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/**").hasAnyRole("USER", "MANAGER", "ADMIN")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.jwtAuthenticationConverter(roleConverter))
                        .authenticationEntryPoint(loggedEntryPoint()))
                .exceptionHandling(e -> e.accessDeniedHandler(loggedAccessDenied()));
        return http.build();
    }

    /**
     * 401 refused by the security before any controller (missing, invalid or expired token): one WARN line (ADR 0008,
     * A3), with the reason of Spring Security and never the token.
     */
    private static org.springframework.security.web.AuthenticationEntryPoint loggedEntryPoint() {
        var delegate = new BearerTokenAuthenticationEntryPoint();
        return (request, response, error) -> {
            log.warn("{} {} → 401: token ausente, inválido ou expirado ({})", request.getMethod(),
                    request.getRequestURI(), error.getClass().getSimpleName());
            delegate.commence(request, response, error);
        };
    }

    /** 403 refused by the route rules (token without a role of the system): one WARN line (ADR 0008, A3). */
    private static org.springframework.security.web.access.AccessDeniedHandler loggedAccessDenied() {
        var delegate = new BearerTokenAccessDeniedHandler();
        return (request, response, error) -> {
            log.warn("{} {} → 403: perfil sem acesso a esta rota", request.getMethod(), request.getRequestURI());
            delegate.handle(request, response, error);
        };
    }

    /**
     * Keys fetched from Keycloak's internal address, but the validated issuer is the public address, which is the one
     * in the token issued to the browser.
     */
    @Bean
    JwtDecoder jwtDecoder(OAuth2ResourceServerProperties properties) {
        var jwt = properties.getJwt();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwt.getJwkSetUri()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(jwt.getIssuerUri()));
        return decoder;
    }

    /** Used by the REST API and by the gRPC server: the same token is valid in both. */
    @Bean
    JwtAuthenticationConverter roleConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("preferred_username");
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realm = jwt.getClaimAsMap("realm_access");
            Object roles = realm == null ? List.of() : realm.getOrDefault("roles", List.of());
            return ((Collection<?>) roles).stream()
                    .map(Object::toString)
                    .filter(p -> p.equals("USER") || p.equals("MANAGER") || p.equals("ADMIN"))
                    .<GrantedAuthority>map(p -> new SimpleGrantedAuthority("ROLE_" + p))
                    .toList();
        });
        return converter;
    }
}
