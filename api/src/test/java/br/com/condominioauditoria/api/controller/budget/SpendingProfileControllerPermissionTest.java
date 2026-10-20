package br.com.condominioauditoria.api.controller.budget;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.dto.request.budget.SpendingProfileBatchRequest;
import br.com.condominioauditoria.api.service.budget.SpendingProfileService;
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

/** RF-13.3: every role reads the spending profiles; only the Admin confirms them (Gestor and Usuário get 403). */
class SpendingProfileControllerPermissionTest {

    private static final UUID CONDOMINIUM = UUID.randomUUID();
    private static final UUID ID = UUID.randomUUID();

    private AnnotationConfigApplicationContext context;
    private SpendingProfileController controller;
    private SpendingProfileService service;

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        CondominiumAccess access() {
            return new CondominiumAccess();
        }
        @Bean
        SpendingProfileService service() {
            return mock(SpendingProfileService.class);
        }

        @Bean
        SpendingProfileController controller(CondominiumAccess access, SpendingProfileService service) {
            return new SpendingProfileController(access, service);
        }
    }

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(Config.class);
        controller = context.getBean(SpendingProfileController.class);
        service = context.getBean(SpendingProfileService.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    private static final SpendingProfileBatchRequest REQUEST = new SpendingProfileBatchRequest(List.of(), null);

    @Test
    void allCondominiumRolesRead() {
        for (String role : List.of("USER", "MANAGER", "ADMIN")) {
            logIn(CONDOMINIUM, "pessoa-" + role, role);
            controller.list(CONDOMINIUM, ID);
            controller.events(CONDOMINIUM, ID);
        }
        verify(service, times(3)).list(CONDOMINIUM, ID);
    }

    @Test
    void managerAndUserCannotConfirm() {
        for (String role : List.of("USER", "MANAGER")) {
            logIn(CONDOMINIUM, "pessoa-" + role, role);
            assertThatThrownBy(() -> controller.confirm(CONDOMINIUM, ID, REQUEST))
                    .isInstanceOf(AuthorizationDeniedException.class);
        }
        verify(service, never()).confirm(any(), any(), any(), any());
    }

    @Test
    void adminConfirms() {
        logIn(CONDOMINIUM, "admin", "ADMIN");
        controller.confirm(CONDOMINIUM, ID, REQUEST);
        verify(service).confirm(CONDOMINIUM, ID, REQUEST, "admin");
    }

    private static void logIn(UUID condominium, String username, String role) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(username).claim("preferred_username", username)
                .claim("condominiums", List.of(condominium.toString())).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)), username));
    }
}
