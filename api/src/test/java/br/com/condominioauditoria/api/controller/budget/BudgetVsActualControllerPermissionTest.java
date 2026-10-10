package br.com.condominioauditoria.api.controller.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import br.com.condominioauditoria.api.dto.response.budget.ExportedFileResponse;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.budget.BudgetVsActualExportService;
import br.com.condominioauditoria.api.service.budget.BudgetVsActualQueryService;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator;
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
import org.springframework.web.server.ResponseStatusException;

/** RF-03.1.13: budget vs. actual is read by every role of the condominium, and only of its own condominium. */
class BudgetVsActualControllerPermissionTest {

    private static final UUID CONDOMINIUM = UUID.randomUUID();

    private AnnotationConfigApplicationContext context;
    private BudgetVsActualController controller;
    private BudgetVsActualQueryService query;

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        CondominiumAccess access() {
            return new CondominiumAccess();
        }

        @Bean
        BudgetVsActualQueryService query() {
            return mock(BudgetVsActualQueryService.class);
        }

        @Bean
        BudgetVsActualExportService exportService() {
            BudgetVsActualExportService e = mock(BudgetVsActualExportService.class);
            org.mockito.Mockito.when(e.export(any(), any(), any(), any(), any(), any())).thenReturn(
                    new ExportedFileResponse("previsto-realizado-2026-09.pdf", "application/pdf",
                            new byte[] {1}));
            return e;
        }

        @Bean
        BudgetVsActualController controller(CondominiumAccess access, BudgetVsActualQueryService query,
                BudgetVsActualExportService export) {
            return new BudgetVsActualController(access, query, export);
        }
    }

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(Config.class);
        controller = context.getBean(BudgetVsActualController.class);
        query = context.getBean(BudgetVsActualQueryService.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void allCondominiumRolesRead() {
        for (String role : List.of("USUARIO", "GESTOR", "ADMIN")) {
            logIn(CONDOMINIUM, "pessoa-" + role, role);
            controller.get(CONDOMINIUM, "2026-09", null, null);
            controller.evidence(CONDOMINIUM, "2026-09", null, "ADJUSTMENTS");
        }
        verify(query, org.mockito.Mockito.times(3)).get(CONDOMINIUM, "2026-09", null);
    }

    @Test
    void allCondominiumRolesExport() {
        BudgetVsActualExportService export = context.getBean(BudgetVsActualExportService.class);
        for (String role : List.of("USUARIO", "GESTOR", "ADMIN")) {
            logIn(CONDOMINIUM, "pessoa-" + role, role);
            var response = controller.export(CONDOMINIUM, "pdf", "2026-09", null, null);
            assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/pdf");
            assertThat(response.getHeaders().getContentDisposition().getFilename())
                    .isEqualTo("previsto-realizado-2026-09.pdf");
        }
        verify(export, org.mockito.Mockito.times(3)).export(any(), any(), any(), any(), any(), any());
    }

    @Test
    void otherCondominiumDoesNotExport() {
        logIn(UUID.randomUUID(), "gestor-de-outro", "GESTOR");

        assertThatThrownBy(() -> controller.export(CONDOMINIUM, "xlsx", "2026-09", null, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void otherCondominiumDoesNotRead() {
        logIn(UUID.randomUUID(), "gestor-de-outro", "GESTOR");

        assertThatThrownBy(() -> controller.get(CONDOMINIUM, "2026-09", null, null))
                .isInstanceOf(AccessDeniedException.class);
        verify(query, never()).get(any(), any(), any());
    }

    @Test
    void invalidPeriodIsRejected() {
        assertThat(BudgetVsActualQueryService.period("2026-09")).isEqualTo(new BudgetVsActualCalculator.Month(
                java.time.YearMonth.of(2026, 9)));
        assertThat(BudgetVsActualQueryService.period("ACUMULADO")).isInstanceOf(BudgetVsActualCalculator.Cumulative.class);
        assertThatThrownBy(() -> BudgetVsActualQueryService.period("09/2026")).isInstanceOf(ResponseStatusException.class);
    }

    private static void logIn(UUID condominium, String username, String role) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(username).claim("preferred_username", username)
                .claim("condominios", List.of(condominium.toString())).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)), username));
    }
}
