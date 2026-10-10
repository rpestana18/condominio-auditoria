package br.com.condominioauditoria.api.controller.feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.api.config.properties.FeatureCatalogTest;
import br.com.condominioauditoria.api.dto.request.feature.ChangeFeatureRequest;
import br.com.condominioauditoria.api.dto.response.feature.FeatureResponse;
import br.com.condominioauditoria.api.model.condominium.Condominium;
import br.com.condominioauditoria.api.report.UsageExcelReport;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.ai.AiCatalog;
import br.com.condominioauditoria.api.service.ai.AiConfigurationService;
import br.com.condominioauditoria.api.service.condominium.CondominiumService;
import br.com.condominioauditoria.api.service.feature.FeatureService;
import br.com.condominioauditoria.api.service.feature.FeatureService.FeatureState;
import br.com.condominioauditoria.api.service.usage.UsageReportService;
import br.com.condominioauditoria.api.service.usage.UsageService;
import br.com.condominioauditoria.api.service.usage.UsageService.UsageSummary;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
 * Feature permissions (§4, RF-10.2, Q17) with Spring's real method security: User and Manager see the context and the
 * feature list of their own condominium; only ADMIN enables, disables, sees trail, periods, usage and exports.
 */
class FeatureControllerPermissionTest {

    private static final UUID PILOT = UUID.fromString("6f1d2c1e-3b4a-4c8e-9a51-2815a0000001");
    private static final UUID OTHER = UUID.randomUUID();
    private static final LocalDate START = LocalDate.of(2026, 10, 1);
    private static final LocalDate END = LocalDate.of(2026, 10, 31);

    private static AnnotationConfigApplicationContext context;
    private static FeatureController controller;
    private static FeatureService features;
    private static UsageService usage;
    private static CondominiumRepository condominiums;

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        FeatureService features() {
            return mock(FeatureService.class);
        }

        @Bean
        UsageService usage() {
            return mock(UsageService.class);
        }

        @Bean
        CondominiumRepository condominiums() {
            return mock(CondominiumRepository.class);
        }

        @Bean
        AiConfigurationService aiConfig() {
            return mock(AiConfigurationService.class);
        }

        @Bean
        AiCatalog aiCatalog() {
            return mock(AiCatalog.class);
        }

        @Bean
        CondominiumAccess access() {
            return new CondominiumAccess();
        }

        @Bean
        CondominiumService condominiumService(CondominiumRepository condominiums, FeatureService features,
                AiConfigurationService aiConfig) {
            return new CondominiumService(condominiums, features, aiConfig);
        }

        @Bean
        UsageReportService usageReports(UsageService usage, FeatureService features,
                CondominiumService condominiumService, AiCatalog aiCatalog) {
            return new UsageReportService(usage, features, condominiumService, aiCatalog);
        }

        @Bean
        FeatureController featureController(FeatureService features, UsageReportService usageReports,
                CondominiumService condominiumService, CondominiumAccess access) {
            return new FeatureController(features, usageReports, condominiumService, access);
        }
    }

    @BeforeAll
    static void startContext() {
        context = new AnnotationConfigApplicationContext(Config.class);
        controller = context.getBean(FeatureController.class);
        features = context.getBean(FeatureService.class);
        usage = context.getBean(UsageService.class);
        condominiums = context.getBean(CondominiumRepository.class);
    }

    @AfterAll
    static void closeContext() {
        context.close();
    }

    @BeforeEach
    void setUp() throws Exception {
        reset(features, usage, condominiums);
        var catalog = FeatureCatalogTest.load();
        var state = new FeatureState(catalog.require(FeatureService.ASSISTANT), true, Instant.now(), 1);
        when(features.catalog()).thenReturn(catalog);
        when(features.states(any())).thenReturn(List.of(state));
        when(features.enabledCodes(any())).thenReturn(List.of(FeatureService.ASSISTANT));
        when(features.change(any(), anyString(), anyBoolean(), any(), any())).thenReturn(state);
        when(usage.summary(any(), any(), any())).thenReturn(new UsageSummary(PILOT, START, END, List.of(), List.of()));
        Condominium condominium = mock(Condominium.class);
        when(condominium.getId()).thenReturn(PILOT);
        when(condominium.getName()).thenReturn("Mio Residencial Parque");
        when(condominiums.findById(PILOT)).thenReturn(Optional.of(condominium));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void userAndManagerSeeContextAndFeaturesOfTheirCondominium() {
        for (String role : List.of("USER", "MANAGER")) {
            logIn(role);
            assertThat(controller.context(PILOT).enabledFeatures()).containsExactly(FeatureService.ASSISTANT);
            assertThat(controller.list(PILOT)).extracting(FeatureResponse::code)
                    .containsExactly(FeatureService.ASSISTANT);
        }
    }

    @Test
    void withoutCondominiumAccessCannotSeeEvenTheContext() {
        logIn("MANAGER");
        assertThatThrownBy(() -> controller.context(OTHER)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.list(OTHER)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void userAndManagerCannotEnableOrDisable() {
        for (String role : List.of("USER", "MANAGER")) {
            logIn(role);
            var request = new ChangeFeatureRequest(true, "x");
            assertThatThrownBy(() -> controller.change(PILOT, FeatureService.ASSISTANT, request))
                    .isInstanceOf(AccessDeniedException.class);
        }
        verify(features, never()).change(any(), anyString(), anyBoolean(), any(), any());
    }

    @Test
    void userAndManagerCannotSeeTrailPeriodsOrUsage() {
        for (String role : List.of("USER", "MANAGER")) {
            logIn(role);
            assertThatThrownBy(() -> controller.events(PILOT, FeatureService.ASSISTANT))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.periods(PILOT, FeatureService.ASSISTANT))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.usage(PILOT, START, END)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.export(PILOT, START, END)).isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    void adminChangesWithTheirOwnUserInTheTrail() {
        logIn("ADMIN");

        controller.change(PILOT, FeatureService.ASSISTANT, new ChangeFeatureRequest(false, "Fim do contrato"));

        verify(features).change(PILOT, FeatureService.ASSISTANT, false, "Fim do contrato", "pessoa.admin");
    }

    @Test
    void adminSeesTrailPeriodsUsageAndExports() {
        logIn("ADMIN");

        controller.events(PILOT, FeatureService.ASSISTANT);
        controller.periods(PILOT, FeatureService.ASSISTANT);
        assertThat(controller.usage(PILOT, START, END).condominiumId()).isEqualTo(PILOT);
        assertThat(controller.usage(PILOT, START, END).costAvailable()).isFalse(); // no catalog from the rag: still works
        var export = controller.export(PILOT, START, END);

        assertThat(export.getHeaders().getContentType().toString()).isEqualTo(UsageExcelReport.CONTENT_TYPE);
        assertThat(export.getHeaders().getContentDisposition().getFilename())
                .isEqualTo("uso-modulos-2026-10-01-a-2026-10-31.xlsx");
    }

    /** Token like Keycloak's: role in the realm and list of condominiums (the Admin sees all). */
    private static void logIn(String role) {
        Jwt jwt = new Jwt("t", Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "none"),
                Map.of("preferred_username", "pessoa." + role.toLowerCase(), "condominiums",
                        List.of(PILOT.toString())));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)), "pessoa." + role.toLowerCase()));
    }
}
