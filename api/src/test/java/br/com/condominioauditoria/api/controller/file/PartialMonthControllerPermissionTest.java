package br.com.condominioauditoria.api.controller.file;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.dto.request.file.PartialMonthRequest;
import br.com.condominioauditoria.api.service.file.PartialMonthService;
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

/** RF-13.10: every role reads the partial months; the Manager or the Admin marks them (Usuário gets 403). */
class PartialMonthControllerPermissionTest {

    private static final UUID CONDOMINIUM = UUID.randomUUID();
    private static final UUID ID = UUID.randomUUID();

    private AnnotationConfigApplicationContext context;
    private PartialMonthController controller;
    private PartialMonthService service;

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        CondominiumAccess access() {
            return new CondominiumAccess();
        }

        @Bean
        PartialMonthService service() {
            return mock(PartialMonthService.class);
        }

        @Bean
        PartialMonthController controller(CondominiumAccess access, PartialMonthService service) {
            return new PartialMonthController(access, service);
        }
    }

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(Config.class);
        controller = context.getBean(PartialMonthController.class);
        service = context.getBean(PartialMonthService.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    private static final PartialMonthRequest REQUEST = new PartialMonthRequest(true, null, null);

    @Test
    void allCondominiumRolesRead() {
        for (String role : List.of("USER", "MANAGER", "ADMIN")) {
            logIn(CONDOMINIUM, "pessoa-" + role, role);
            controller.list(CONDOMINIUM);
            controller.events(CONDOMINIUM, ID);
        }
        verify(service, times(3)).list(CONDOMINIUM);
    }

    @Test
    void userCannotMark() {
        logIn(CONDOMINIUM, "usuario", "USER");
        assertThatThrownBy(() -> controller.mark(CONDOMINIUM, ID, REQUEST))
                .isInstanceOf(AuthorizationDeniedException.class);
        verify(service, never()).mark(any(), any(), any(), any());
    }

    @Test
    void managerAndAdminMark() {
        for (String role : List.of("MANAGER", "ADMIN")) {
            logIn(CONDOMINIUM, "pessoa-" + role, role);
            controller.mark(CONDOMINIUM, ID, REQUEST);
            verify(service).mark(CONDOMINIUM, ID, REQUEST, "pessoa-" + role);
        }
    }

    @Test
    void otherCondominiumDoesNotMark() {
        logIn(UUID.randomUUID(), "gestor-de-outro", "MANAGER");
        assertThatThrownBy(() -> controller.mark(CONDOMINIUM, ID, REQUEST)).isInstanceOf(AccessDeniedException.class);
    }

    private static void logIn(UUID condominium, String username, String role) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(username).claim("preferred_username", username)
                .claim("condominiums", List.of(condominium.toString())).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)), username));
    }
}
