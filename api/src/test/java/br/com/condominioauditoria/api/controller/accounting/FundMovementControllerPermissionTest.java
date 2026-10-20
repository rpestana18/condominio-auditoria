package br.com.condominioauditoria.api.controller.accounting;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.accounting.FundMovementService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** RF-13.5: every role reads the funds screen, only of its own condominium. */
class FundMovementControllerPermissionTest {

    private static final UUID CONDOMINIUM = UUID.randomUUID();
    private static final UUID ID = UUID.randomUUID();

    private AnnotationConfigApplicationContext context;
    private FundMovementController controller;
    private FundMovementService service;

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        CondominiumAccess access() {
            return new CondominiumAccess();
        }

        @Bean
        FundMovementService service() {
            return mock(FundMovementService.class);
        }

        @Bean
        FundMovementController controller(CondominiumAccess access, FundMovementService service) {
            return new FundMovementController(access, service);
        }
    }

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(Config.class);
        controller = context.getBean(FundMovementController.class);
        service = context.getBean(FundMovementService.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void allCondominiumRolesRead() {
        for (String role : List.of("USER", "MANAGER", "ADMIN")) {
            logIn(CONDOMINIUM, "pessoa-" + role, role);
            controller.get(CONDOMINIUM, "2026-05", "2027-04");
        }
        verify(service, times(3)).get(CONDOMINIUM, "2026-05", "2027-04");
    }

    @Test
    void otherCondominiumDoesNotRead() {
        logIn(UUID.randomUUID(), "gestor-de-outro", "MANAGER");
        assertThatThrownBy(() -> controller.get(CONDOMINIUM, null, null)).isInstanceOf(AccessDeniedException.class);
        verify(service, never()).get(any(), any(), any());
    }

    private static void logIn(UUID condominium, String username, String role) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(username).claim("preferred_username", username)
                .claim("condominiums", List.of(condominium.toString())).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)), username));
    }
}
