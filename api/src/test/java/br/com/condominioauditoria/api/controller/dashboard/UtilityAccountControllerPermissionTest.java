package br.com.condominioauditoria.api.controller.dashboard;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import br.com.condominioauditoria.api.dto.request.dashboard.DecideUtilityAccountRequest;
import br.com.condominioauditoria.api.model.enums.UtilityType;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.dashboard.UtilityAccountService;
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

/** RF-12.6: every role sees the utility accounts; only the Manager or the Admin decides (RF-12.1). */
class UtilityAccountControllerPermissionTest {

    private static final UUID CONDOMINIUM = UUID.randomUUID();
    private static final DecideUtilityAccountRequest WATER = new DecideUtilityAccountRequest(UtilityType.WATER);

    private AnnotationConfigApplicationContext context;
    private UtilityAccountController controller;
    private UtilityAccountService service;

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        CondominiumAccess access() {
            return new CondominiumAccess();
        }

        @Bean
        UtilityAccountService service() {
            return mock(UtilityAccountService.class);
        }

        @Bean
        UtilityAccountController controller(CondominiumAccess access, UtilityAccountService service) {
            return new UtilityAccountController(access, service);
        }
    }

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(Config.class);
        controller = context.getBean(UtilityAccountController.class);
        service = context.getBean(UtilityAccountService.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void userReadsButDoesNotDecide() {
        logIn(CONDOMINIUM, "usuario", "USER");
        controller.list(CONDOMINIUM);
        assertThatThrownBy(() -> controller.decide(CONDOMINIUM, "1896", WATER))
                .isInstanceOf(AccessDeniedException.class);
        verify(service).list(CONDOMINIUM);
    }

    @Test
    void managerAndAdminDecide() {
        logIn(CONDOMINIUM, "gestor", "MANAGER");
        controller.decide(CONDOMINIUM, "1896", WATER);
        logIn(CONDOMINIUM, "admin", "ADMIN");
        controller.decide(CONDOMINIUM, "3775", new DecideUtilityAccountRequest(null));
        verify(service).decide(CONDOMINIUM, "1896", UtilityType.WATER, "gestor");
        verify(service).decide(CONDOMINIUM, "3775", null, "admin");
    }

    @Test
    void managerOfOtherCondominiumDoesNothing() {
        logIn(UUID.randomUUID(), "gestor", "MANAGER");
        assertThatThrownBy(() -> controller.list(CONDOMINIUM)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.decide(CONDOMINIUM, "1896", WATER))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(service);
    }

    private static void logIn(UUID condominium, String username, String role) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(username).claim("preferred_username", username)
                .claim("condominiums", List.of(condominium.toString())).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)), username));
    }
}
