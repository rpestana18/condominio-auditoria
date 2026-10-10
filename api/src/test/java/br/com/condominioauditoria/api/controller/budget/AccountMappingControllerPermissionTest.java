package br.com.condominioauditoria.api.controller.budget;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import br.com.condominioauditoria.api.dto.request.budget.AccountMappingBatchRequest;
import br.com.condominioauditoria.api.dto.request.budget.MappingTargetRequest;
import br.com.condominioauditoria.api.model.enums.AccountMappingBatchAction;
import br.com.condominioauditoria.api.model.enums.MappingTargetType;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.budget.AccountMappingService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** RF-03.1.4 and RF-03.1.13: only the Admin creates, changes, confirms and rejects the mapping; every role reads it. */
class AccountMappingControllerPermissionTest {

    private static final UUID CONDOMINIUM = UUID.randomUUID();
    private static final UUID BUDGET = UUID.randomUUID();

    private AnnotationConfigApplicationContext context;
    private AccountMappingController controller;
    private AccountMappingService service;

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        CondominiumAccess access() {
            return new CondominiumAccess();
        }

        @Bean
        AccountMappingService service() {
            return mock(AccountMappingService.class);
        }

        @Bean
        AccountMappingController controller(CondominiumAccess access, AccountMappingService service) {
            return new AccountMappingController(access, service);
        }
    }

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(Config.class);
        controller = context.getBean(AccountMappingController.class);
        service = context.getBean(AccountMappingService.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void managerAndUserDoNotWrite() {
        for (String role : List.of("MANAGER", "USER")) {
            logIn(CONDOMINIUM, "pessoa-" + role, role);
            for (Executable write : writes()) {
                assertThatThrownBy(write::execute).isInstanceOf(AccessDeniedException.class);
            }
        }
        verifyNoInteractions(service);
    }

    @Test
    void adminWrites() throws Throwable {
        logIn(CONDOMINIUM, "admin", "ADMIN");
        for (Executable write : writes()) {
            write.execute();
        }
        verify(service).suggest(CONDOMINIUM, BUDGET, "admin");
        verify(service).setTarget(eq(CONDOMINIUM), eq(BUDGET), eq("1621"), any(), eq("admin"));
        verify(service).batch(eq(CONDOMINIUM), eq(BUDGET), any(), eq("admin"));
        verify(service).loadSheet(eq(CONDOMINIUM), eq(BUDGET), eq("mapa.csv"), eq("1621;1.7.8\n"), eq("admin"));
    }

    @Test
    void allRolesRead() {
        for (String role : List.of("USER", "MANAGER", "ADMIN")) {
            logIn(CONDOMINIUM, "pessoa-" + role, role);
            controller.list(CONDOMINIUM, BUDGET, null);
            controller.events(CONDOMINIUM, BUDGET);
        }
    }

    @Test
    void otherCondominiumDoesNotRead() {
        logIn(UUID.randomUUID(), "gestor-de-outro", "MANAGER");

        assertThatThrownBy(() -> controller.list(CONDOMINIUM, BUDGET, null)).isInstanceOf(AccessDeniedException.class);
        verify(service, never()).list(any(), any(), any());
    }

    private List<Executable> writes() {
        return List.of(
                () -> controller.suggest(CONDOMINIUM, BUDGET),
                () -> controller.setTarget(CONDOMINIUM, BUDGET, "1621",
                        new MappingTargetRequest(MappingTargetType.ADJUSTMENT, null, null, null)),
                () -> controller.batch(CONDOMINIUM, BUDGET,
                        new AccountMappingBatchRequest(AccountMappingBatchAction.CONFIRM, List.of("1621"))),
                () -> controller.uploadSheet(CONDOMINIUM, BUDGET, new MockMultipartFile("file", "mapa.csv",
                        "text/csv",
                        "1621;1.7.8\n".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    }

    private static void logIn(UUID condominium, String username, String role) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(username).claim("preferred_username", username)
                .claim("condominiums", List.of(condominium.toString())).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)), username));
    }
}
