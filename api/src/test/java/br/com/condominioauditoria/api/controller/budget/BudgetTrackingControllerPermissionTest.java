package br.com.condominioauditoria.api.controller.budget;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.budget.BalanceBridgeService;
import br.com.condominioauditoria.api.service.budget.BudgetTrackingService;
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

/** RF-13.4 and RF-13.6: the tracking grid and the balance bridge are read by every role, only of its own condominium. */
class BudgetTrackingControllerPermissionTest {

    private static final UUID CONDOMINIUM = UUID.randomUUID();
    private static final UUID ID = UUID.randomUUID();

    private AnnotationConfigApplicationContext context;
    private BudgetTrackingController controller;
    private BudgetTrackingService tracking;
    private BalanceBridgeService bridge;

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        CondominiumAccess access() {
            return new CondominiumAccess();
        }
        @Bean
        BudgetTrackingService tracking() {
            return mock(BudgetTrackingService.class);
        }

        @Bean
        BalanceBridgeService bridge() {
            return mock(BalanceBridgeService.class);
        }

        @Bean
        BudgetTrackingController controller(CondominiumAccess access, BudgetTrackingService tracking,
                BalanceBridgeService bridge) {
            return new BudgetTrackingController(access, tracking, bridge);
        }
    }

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(Config.class);
        controller = context.getBean(BudgetTrackingController.class);
        tracking = context.getBean(BudgetTrackingService.class);
        bridge = context.getBean(BalanceBridgeService.class);
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
            controller.grid(CONDOMINIUM, null);
            controller.bridge(CONDOMINIUM, "all", "2026-05", "2027-04");
        }
        verify(tracking, times(3)).get(CONDOMINIUM, null);
        verify(bridge, times(3)).get(CONDOMINIUM, "all", "2026-05", "2027-04");
    }

    @Test
    void otherCondominiumDoesNotRead() {
        logIn(UUID.randomUUID(), "gestor-de-outro", "MANAGER");

        assertThatThrownBy(() -> controller.grid(CONDOMINIUM, null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.bridge(CONDOMINIUM, null, null, null))
                .isInstanceOf(AccessDeniedException.class);
        verify(tracking, never()).get(any(), any());
    }

    private static void logIn(UUID condominium, String username, String role) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(username).claim("preferred_username", username)
                .claim("condominiums", List.of(condominium.toString())).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)), username));
    }
}
