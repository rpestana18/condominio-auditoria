package br.com.condominioauditoria.api.security;

import jakarta.servlet.DispatcherType;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
import org.springframework.security.web.SecurityFilterChain;

/**
 * The api is a "resource server": it only accepts a valid Bearer token issued by Keycloak. Roles come from
 * realm_access.roles (USUARIO, GESTOR, ADMIN) and become ROLE_USUARIO etc.
 */
@Configuration
@EnableMethodSecurity
class SecurityConfig {

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
                        .requestMatchers("/api/**").hasAnyRole("USUARIO", "GESTOR", "ADMIN")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.jwtAuthenticationConverter(roleConverter)));
        return http.build();
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
                    .filter(p -> p.equals("USUARIO") || p.equals("GESTOR") || p.equals("ADMIN"))
                    .<GrantedAuthority>map(p -> new SimpleGrantedAuthority("ROLE_" + p))
                    .toList();
        });
        return converter;
    }
}
