package br.com.condominioauditoria.mcp.config;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 * O endpoint MCP só aceita token Bearer válido do Keycloak, com perfil no sistema. O mesmo token segue para o
 * backend em cada chamada gRPC, e é lá que o acesso a cada condomínio é conferido.
 */
@Configuration
class SegurancaConfig {

    @Bean
    SecurityFilterChain seguranca(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .anyRequest().hasAnyRole("USUARIO", "GESTOR", "ADMIN"))
                .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.jwtAuthenticationConverter(conversorDePerfis())));
        return http.build();
    }

    /** Chaves pelo endereço interno do Keycloak; emissor validado é o endereço público (o que está no token). */
    @Bean
    JwtDecoder jwtDecoder(OAuth2ResourceServerProperties propriedades) {
        var jwt = propriedades.getJwt();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwt.getJwkSetUri()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(jwt.getIssuerUri()));
        return decoder;
    }

    private static JwtAuthenticationConverter conversorDePerfis() {
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
