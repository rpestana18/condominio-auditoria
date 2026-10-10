package br.com.condominioauditoria.api.controller.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.api.dto.request.budget.BudgetConfirmationRequest;
import br.com.condominioauditoria.api.dto.request.budget.BudgetFundsRequest;
import br.com.condominioauditoria.api.dto.response.budget.BudgetDetailResponse;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.budget.BudgetConfirmationService;
import br.com.condominioauditoria.api.service.budget.BudgetFundLinkService;
import br.com.condominioauditoria.api.service.budget.BudgetQueryService;
import java.util.List;
import java.util.Optional;
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
 * RF-03.1.3 and RF-03.1.13: reading the budget applies to every role of the condominium; confirming, only the Admin.
 * The denial becomes 403 in the API (AccessDeniedException handled by Spring Security). Uses the real method security
 * ({@code @EnableMethodSecurity}, as in SecurityConfig).
 */
class BudgetControllerPermissionTest {

    private static final UUID CONDOMINIUM = UUID.randomUUID();
    private static final UUID BUDGET = UUID.randomUUID();

    private AnnotationConfigApplicationContext context;
    private BudgetController controller;
    private BudgetQueryService query;
    private BudgetConfirmationService confirmation;

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        CondominiumAccess access() {
            return new CondominiumAccess();
        }

        @Bean
        BudgetQueryService query() {
            BudgetQueryService query = mock(BudgetQueryService.class);
            when(query.events(CONDOMINIUM, BUDGET)).thenReturn(Optional.of(List.of()));
            return query;
        }

        @Bean
        BudgetConfirmationService confirmation() {
            return mock(BudgetConfirmationService.class);
        }

        @Bean
        BudgetFundLinkService fundLinks() {
            return mock(BudgetFundLinkService.class);
        }

        @Bean
        BudgetController controller(CondominiumAccess access, BudgetQueryService query,
                BudgetConfirmationService confirmation, BudgetFundLinkService fundLinks) {
            return new BudgetController(access, query, confirmation, fundLinks);
        }
    }

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(Config.class);
        controller = context.getBean(BudgetController.class);
        query = context.getBean(BudgetQueryService.class);
        confirmation = context.getBean(BudgetConfirmationService.class);
        when(query.list(CONDOMINIUM)).thenReturn(List.of());
        when(query.detail(CONDOMINIUM, BUDGET)).thenReturn(Optional.of(mock(BudgetDetailResponse.class)));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void managerDoesNotConfirm() {
        logIn("gestor", "MANAGER");

        assertThatThrownBy(() -> controller.confirm(CONDOMINIUM, BUDGET, request()))
                .isInstanceOf(AccessDeniedException.class);
        verify(confirmation, never()).confirm(any(), any(), any(), any());
    }

    @Test
    void userDoesNotConfirm() {
        logIn("morador", "USER");

        assertThatThrownBy(() -> controller.confirm(CONDOMINIUM, BUDGET, request()))
                .isInstanceOf(AccessDeniedException.class);
        verify(confirmation, never()).confirm(any(), any(), any(), any());
    }

    @Test
    void adminConfirms() {
        logIn("admin", "ADMIN");

        controller.confirm(CONDOMINIUM, BUDGET, request());

        verify(confirmation).confirm(eq(CONDOMINIUM), eq(BUDGET), any(), eq("admin"));
    }

    @Test
    void onlyAdminChangesFundLinks() {
        BudgetFundLinkService fundLinks = context.getBean(BudgetFundLinkService.class);
        var request = new BudgetFundsRequest(List.of());
        for (String role : List.of("USER", "MANAGER")) {
            logIn("pessoa-" + role, role);
            assertThatThrownBy(() -> controller.changeFunds(CONDOMINIUM, BUDGET, request))
                    .isInstanceOf(AccessDeniedException.class);
        }
        verify(fundLinks, never()).change(any(), any(), any(), any());

        logIn("admin", "ADMIN");
        controller.changeFunds(CONDOMINIUM, BUDGET, request);
        verify(fundLinks).change(eq(CONDOMINIUM), eq(BUDGET), eq(request), eq("admin"));
    }

    @Test
    void allRolesReadBudgetTrail() {
        for (String role : List.of("USER", "MANAGER", "ADMIN")) {
            logIn("pessoa-" + role, role);
            assertThat(controller.events(CONDOMINIUM, BUDGET)).isEmpty();
        }
        logInTo(UUID.randomUUID(), "gestor-de-outro", "MANAGER");
        assertThatThrownBy(() -> controller.events(CONDOMINIUM, BUDGET)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void allCondominiumRolesRead() {
        for (String role : List.of("USER", "MANAGER", "ADMIN")) {
            logIn("pessoa-" + role, role);

            assertThat(controller.list(CONDOMINIUM)).isEmpty();
            assertThat(controller.detail(CONDOMINIUM, BUDGET)).isNotNull();
        }
    }

    @Test
    void outsiderDoesNotRead() {
        logInTo(UUID.randomUUID(), "gestor-de-outro", "MANAGER");

        assertThatThrownBy(() -> controller.list(CONDOMINIUM)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.detail(CONDOMINIUM, BUDGET)).isInstanceOf(AccessDeniedException.class);
    }

    private static BudgetConfirmationRequest request() {
        return new BudgetConfirmationRequest("2026-05", "2027-04", null, true, null, List.of(), List.of(), false,
                false, null);
    }

    private static void logIn(String username, String role) {
        logInTo(CONDOMINIUM, username, role);
    }

    private static void logInTo(UUID condominium, String username, String role) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(username).claim("preferred_username", username)
                .claim("condominiums", List.of(condominium.toString())).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)), username));
    }
}
