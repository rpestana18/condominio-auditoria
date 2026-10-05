package br.com.condominioauditoria.backend.config;

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
 * O backend é um "resource server": só aceita token Bearer válido emitido pelo Keycloak.
 * Perfis vêm de realm_access.roles (USUARIO, GESTOR, ADMIN) e viram ROLE_USUARIO etc.
 */
@Configuration
@EnableMethodSecurity
class SegurancaConfig {

    @Bean
    SecurityFilterChain seguranca(HttpSecurity http, JwtAuthenticationConverter conversorDePerfis) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // API sem cookie de sessão: o token vai no cabeçalho
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Página de erro do Spring: sem isto, 400 e 404 tratados pelo próprio Spring viram 403 vazio
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/**").hasAnyRole("USUARIO", "GESTOR", "ADMIN")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.jwtAuthenticationConverter(conversorDePerfis)));
        return http.build();
    }

    /**
     * Chaves buscadas no endereço interno do Keycloak, mas o emissor validado é o endereço público,
     * que é o que aparece no token emitido para o navegador.
     */
    @Bean
    JwtDecoder jwtDecoder(OAuth2ResourceServerProperties propriedades) {
        var jwt = propriedades.getJwt();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwt.getJwkSetUri()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(jwt.getIssuerUri()));
        return decoder;
    }

    /** Usado pela API REST e pelo servidor gRPC: o mesmo token vale nos dois. */
    @Bean
    JwtAuthenticationConverter conversorDePerfis() {
        var conversor = new JwtAuthenticationConverter();
        conversor.setPrincipalClaimName("preferred_username");
        conversor.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realm = jwt.getClaimAsMap("realm_access");
            Object perfis = realm == null ? List.of() : realm.getOrDefault("roles", List.of());
            return ((Collection<?>) perfis).stream()
                    .map(Object::toString)
                    .filter(p -> p.equals("USUARIO") || p.equals("GESTOR") || p.equals("ADMIN"))
                    .<GrantedAuthority>map(p -> new SimpleGrantedAuthority("ROLE_" + p))
                    .toList();
        });
        return conversor;
    }
}
