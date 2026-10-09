package br.com.condominioauditoria.api.controller.budget;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import br.com.condominioauditoria.api.dto.request.budget.ReallocationRequest;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.budget.ReallocationService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** RF-03.1.7 and ADR 0004, Decision 8: only Gestor and Admin reallocate and undo; every role reads. */
class ReallocationControllerPermissionTest {

    private static final UUID CONDOMINIUM = UUID.randomUUID();
    private static final ReallocationRequest REQUEST = new ReallocationRequest(UUID.randomUUID(), UUID.randomUUID());

    private AnnotationConfigApplicationContext context;
    private ReallocationController controller;
    private ReallocationService service;

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        CondominiumAccess access() {
            return new CondominiumAccess();
        }

        @Bean
        ReallocationService service() {
            return mock(ReallocationService.class);
        }

        @Bean
        ReallocationController controller(CondominiumAccess access, ReallocationService service) {
            return new ReallocationController(access, service);
        }
    }

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(Config.class);
        controller = context.getBean(ReallocationController.class);
        service = context.getBean(ReallocationService.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void userReadsButNeitherReallocatesNorUndoes() {
        logIn(CONDOMINIUM, "usuario", "USUARIO");

        controller.list(CONDOMINIUM, UUID.randomUUID());
        assertThatThrownBy(() -> controller.reallocate(CONDOMINIUM, REQUEST)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.undo(CONDOMINIUM, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
        verify(service, never()).reallocate(any(), any(), any());
        verify(service, never()).undo(any(), any(), any());
    }

    @Test
    void managerAndAdminReallocateAndUndo() {
        for (String role : List.of("GESTOR", "ADMIN")) {
            logIn(CONDOMINIUM, "pessoa-" + role, role);
            controller.reallocate(CONDOMINIUM, REQUEST);
            controller.undo(CONDOMINIUM, UUID.randomUUID());
        }
        verify(service, times(2)).reallocate(any(), any(), any());
        verify(service, times(2)).undo(any(), any(), any());
    }

    @Test
    void managerOfOtherCondominiumDoesNotReallocate() {
        logIn(UUID.randomUUID(), "gestor-de-outro", "GESTOR");

        assertThatThrownBy(() -> controller.reallocate(CONDOMINIUM, REQUEST)).isInstanceOf(AccessDeniedException.class);
        verify(service, never()).reallocate(any(), any(), any());
    }

    private static void logIn(UUID condominium, String username, String role) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(username).claim("preferred_username", username)
                .claim("condominios", List.of(condominium.toString())).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)), username));
    }
}
