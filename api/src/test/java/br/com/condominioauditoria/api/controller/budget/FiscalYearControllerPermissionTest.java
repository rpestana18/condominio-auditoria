package br.com.condominioauditoria.api.controller.budget;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.budget.FiscalYearComparisonService;
import br.com.condominioauditoria.api.service.budget.FiscalYearService;
import br.com.condominioauditoria.api.service.budget.IndicatorService;
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

/**
 * RF-11.4 to RF-11.12: every role of the condominium reads fiscal years, printed column, comparison and indicators;
 * another condominium does not.
 */
class FiscalYearControllerPermissionTest {

    private static final UUID CONDOMINIUM = UUID.randomUUID();
    private static final UUID BUDGET = UUID.randomUUID();

    private AnnotationConfigApplicationContext context;
    private FiscalYearController controller;
    private FiscalYearService service;
    private FiscalYearComparisonService comparison;
    private IndicatorService indicators;

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        CondominiumAccess access() {
            return new CondominiumAccess();
        }

        @Bean
        FiscalYearService service() {
            return mock(FiscalYearService.class);
        }

        @Bean
        FiscalYearComparisonService comparison() {
            return mock(FiscalYearComparisonService.class);
        }

        @Bean
        IndicatorService indicators() {
            return mock(IndicatorService.class);
        }

        @Bean
        FiscalYearController controller(CondominiumAccess access, FiscalYearService service,
                FiscalYearComparisonService comparison, IndicatorService indicators) {
            return new FiscalYearController(access, service, comparison, indicators);
        }
    }

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(Config.class);
        controller = context.getBean(FiscalYearController.class);
        service = context.getBean(FiscalYearService.class);
        comparison = context.getBean(FiscalYearComparisonService.class);
        indicators = context.getBean(IndicatorService.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void allRolesRead() {
        for (String role : List.of("USER", "MANAGER", "ADMIN")) {
            logIn(CONDOMINIUM, "pessoa-" + role, role);
            controller.list(CONDOMINIUM);
            controller.printedColumn(CONDOMINIUM, BUDGET);
            controller.compare(CONDOMINIUM, null, null, false);
            controller.indicators(CONDOMINIUM, BUDGET, null);
        }
        verify(indicators, times(3)).indicators(CONDOMINIUM, BUDGET, null);
        verify(comparison, times(3)).compare(CONDOMINIUM, null, null, false);
        verify(service, times(3)).list(CONDOMINIUM);
        verify(service, times(3)).printedColumn(CONDOMINIUM, BUDGET);
    }

    @Test
    void managerOfOtherCondominiumDoesNotRead() {
        logIn(UUID.randomUUID(), "gestor", "MANAGER");
        assertThatThrownBy(() -> controller.list(CONDOMINIUM)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.printedColumn(CONDOMINIUM,
                BUDGET)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.compare(CONDOMINIUM, null, null, false))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.indicators(CONDOMINIUM, BUDGET, null))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(service, comparison, indicators);
    }

    private static void logIn(UUID condominium, String username, String role) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(username).claim("preferred_username", username)
                .claim("condominiums", List.of(condominium.toString())).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)), username));
    }
}
