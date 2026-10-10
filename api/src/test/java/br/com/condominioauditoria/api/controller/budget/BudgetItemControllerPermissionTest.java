package br.com.condominioauditoria.api.controller.budget;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import br.com.condominioauditoria.api.dto.request.budget.BudgetItemBatchRequest;
import br.com.condominioauditoria.api.dto.request.budget.LineBudgetItemRequest;
import br.com.condominioauditoria.api.dto.request.budget.NewBudgetItemRequest;
import br.com.condominioauditoria.api.dto.request.budget.RenameBudgetItemRequest;
import br.com.condominioauditoria.api.model.enums.BudgetItemBatchAction;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.budget.BudgetItemService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** RF-11.7: only the Admin creates, renames, chooses, confirms, rejects and suggests items; every role reads them. */
class BudgetItemControllerPermissionTest {

    private static final UUID CONDOMINIUM = UUID.randomUUID();
    private static final UUID BUDGET = UUID.randomUUID();
    private static final UUID LINE = UUID.randomUUID();
    private static final UUID BUDGET_ITEM = UUID.randomUUID();

    private AnnotationConfigApplicationContext context;
    private BudgetItemController controller;
    private BudgetItemService service;

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        CondominiumAccess access() {
            return new CondominiumAccess();
        }

        @Bean
        BudgetItemService service() {
            return mock(BudgetItemService.class);
        }

        @Bean
        BudgetItemController controller(CondominiumAccess access, BudgetItemService service) {
            return new BudgetItemController(access, service);
        }
    }

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(Config.class);
        controller = context.getBean(BudgetItemController.class);
        service = context.getBean(BudgetItemService.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void managerAndUserDoNotWrite() {
        for (String role : List.of("GESTOR", "USUARIO")) {
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
        verify(service).create(eq(CONDOMINIUM), any(), eq("admin"));
        verify(service).rename(eq(CONDOMINIUM), eq(BUDGET_ITEM), any(), eq("admin"));
        verify(service).setItem(eq(CONDOMINIUM), eq(BUDGET), eq(LINE), any(), eq("admin"));
        verify(service).batch(eq(CONDOMINIUM), eq(BUDGET), any(), eq("admin"));
        verify(service).suggest(CONDOMINIUM, BUDGET, "admin");
    }

    @Test
    void allRolesRead() {
        for (String role : List.of("USUARIO", "GESTOR", "ADMIN")) {
            logIn(CONDOMINIUM, "pessoa-" + role, role);
            controller.catalog(CONDOMINIUM);
            controller.list(CONDOMINIUM, BUDGET, null);
            controller.events(CONDOMINIUM, BUDGET);
        }
    }

    @Test
    void otherCondominiumDoesNotRead() {
        logIn(UUID.randomUUID(), "gestor-de-outro", "GESTOR");

        assertThatThrownBy(() -> controller.list(CONDOMINIUM, BUDGET, null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.catalog(CONDOMINIUM)).isInstanceOf(AccessDeniedException.class);
        verify(service, never()).list(any(), any(), any());
    }

    private List<Executable> writes() {
        return List.of(
                () -> controller.create(CONDOMINIUM, new NewBudgetItemRequest("Academia", "1.3")),
                () -> controller.rename(CONDOMINIUM, BUDGET_ITEM, new RenameBudgetItemRequest("Academia e ginástica")),
                () -> controller.setItem(CONDOMINIUM, BUDGET, LINE, new LineBudgetItemRequest(BUDGET_ITEM, null, null)),
                () -> controller.batch(CONDOMINIUM, BUDGET, new BudgetItemBatchRequest(BudgetItemBatchAction.CONFIRM,
                        List.of(LINE))),
                () -> controller.suggest(CONDOMINIUM, BUDGET));
    }

    private static void logIn(UUID condominium, String username, String role) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(username).claim("preferred_username", username)
                .claim("condominios", List.of(condominium.toString())).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)), username));
    }
}
