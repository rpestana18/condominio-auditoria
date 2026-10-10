package br.com.condominioauditoria.api.controller.budget;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import br.com.condominioauditoria.api.dto.request.budget.BudgetExtensionRequest;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.budget.BudgetExtensionService;
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

/** RF-11.3: only the Admin marks or undoes the extension; Gestor and Usuário get 403. */
class BudgetExtensionControllerPermissionTest {

    private static final UUID CONDOMINIUM = UUID.randomUUID();
    private static final UUID BUDGET = UUID.randomUUID();

    private AnnotationConfigApplicationContext context;
    private BudgetExtensionController controller;
    private BudgetExtensionService service;

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        CondominiumAccess access() {
            return new CondominiumAccess();
        }

        @Bean
        BudgetExtensionService service() {
            return mock(BudgetExtensionService.class);
        }

        @Bean
        BudgetExtensionController controller(CondominiumAccess access, BudgetExtensionService service) {
            return new BudgetExtensionController(access, service);
        }
    }

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(Config.class);
        controller = context.getBean(BudgetExtensionController.class);
        service = context.getBean(BudgetExtensionService.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void managerAndUserDoNotExtend() {
        for (String role : List.of("MANAGER", "USER")) {
            logIn(CONDOMINIUM, "pessoa-" + role, role);
            assertThatThrownBy(() -> controller.extend(CONDOMINIUM, BUDGET, new BudgetExtensionRequest("2026-04",
                    "atraso")))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.undo(CONDOMINIUM, BUDGET)).isInstanceOf(AccessDeniedException.class);
        }
        verifyNoInteractions(service);
    }

    @Test
    void adminExtendsAndUndoes() {
        logIn(CONDOMINIUM, "admin", "ADMIN");
        controller.extend(CONDOMINIUM, BUDGET, new BudgetExtensionRequest("2026-04", "atraso"));
        controller.undo(CONDOMINIUM, BUDGET);
        verify(service).extend(eq(CONDOMINIUM), eq(BUDGET), any(), eq("admin"));
        verify(service).undo(CONDOMINIUM, BUDGET, "admin");
    }

    private static void logIn(UUID condominium, String username, String role) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(username).claim("preferred_username", username)
                .claim("condominiums", List.of(condominium.toString())).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)), username));
    }
}
