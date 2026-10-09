package br.com.condominioauditoria.api.controller.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.api.dto.request.ai.AiAnswersRequest;
import br.com.condominioauditoria.api.dto.request.ai.AiAssistantRequest;
import br.com.condominioauditoria.api.dto.request.ai.AiConfigurationRequest;
import br.com.condominioauditoria.api.dto.request.ai.AiEmbeddingsRequest;
import br.com.condominioauditoria.api.grpc.client.AssistantClient;
import br.com.condominioauditoria.api.model.ai.AiConfiguration;
import br.com.condominioauditoria.api.model.enums.AiMode;
import br.com.condominioauditoria.api.repository.ai.AiConfigurationEventRepository;
import br.com.condominioauditoria.api.repository.ai.AiConfigurationRepository;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.security.TestKeyPair;
import br.com.condominioauditoria.api.service.ai.AiCatalog;
import br.com.condominioauditoria.api.service.ai.AiConfigurationService;
import br.com.condominioauditoria.api.service.ai.TestCatalog;
import br.com.condominioauditoria.api.service.condominium.CondominiumService;
import br.com.condominioauditoria.api.service.feature.FeatureService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
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
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI configuration in the API (RF-09.6): only ADMIN reads and saves, including the catalog; GESTOR and USUARIO neither
 * change nor see it. The key never appears in the response JSON (neither plain nor encrypted) and the catalog goes out
 * without the public key.
 */
class AiConfigurationControllerTest {

    private static final UUID A = UUID.randomUUID();
    private static final String KEY = "sk-ant-api03-ChaveDeTesteNaoReal-x9Qa";

    private final AiConfigurationRepository configurations = mock(AiConfigurationRepository.class);
    private final AiConfigurationEventRepository events = mock(AiConfigurationEventRepository.class);
    private final AssistantClient rag = mock(AssistantClient.class);
    private final CondominiumRepository condominiums = mock(CondominiumRepository.class);
    private final List<AiConfiguration> rows = new ArrayList<>();
    private AnnotationConfigApplicationContext context;
    private AiConfigurationController controller;

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        AiConfigurationController aiConfigurationController(AiConfigurationService service, AiCatalog catalog,
                CondominiumAccess access, CondominiumService condominiums) {
            return new AiConfigurationController(service, catalog, access, condominiums);
        }
    }

    @BeforeEach
    void setUp() {
        when(condominiums.existsById(A)).thenReturn(true);
        when(configurations.findByCondominiumId(any())).thenAnswer(i -> List.copyOf(rows));
        when(configurations.save(any())).thenAnswer(i -> {
            AiConfiguration c = i.getArgument(0);
            rows.removeIf(l -> l.getId().equals(c.getId()));
            rows.add(c);
            return c;
        });
        when(rag.listProviders(anyString()))
                .thenReturn(TestCatalog.response(TestKeyPair.publicPem(TestKeyPair.pair())));
        var catalog = new AiCatalog(rag, Duration.ofMinutes(5), java.time.Clock.systemUTC());
        var service = new AiConfigurationService(configurations, events, catalog, mock(FeatureService.class),
                TransactionOperations.withoutTransaction());
        context = new AnnotationConfigApplicationContext();
        context.registerBean(AiConfigurationService.class, () -> service);
        context.registerBean(AiCatalog.class, () -> catalog);
        context.registerBean(CondominiumAccess.class, CondominiumAccess::new);
        context.registerBean(CondominiumService.class,
                () -> new CondominiumService(condominiums, mock(FeatureService.class), service));
        context.register(Config.class);
        context.refresh();
        controller = context.getBean(AiConfigurationController.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
        SecurityContextHolder.clearContext();
    }

    @Test
    void managerAndUserNeitherSeeNorChangeAi() {
        for (String role : List.of("USUARIO", "GESTOR")) {
            logIn(role);
            assertThatThrownBy(() -> controller.read(A)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.save(A, request(KEY))).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.providers()).isInstanceOf(AccessDeniedException.class);
        }
        verify(configurations, never()).save(any());
        verify(rag, never()).listProviders(anyString());
    }

    @Test
    void adminSavesAndKeyNeverComesBackInJson() throws Exception {
        logIn("ADMIN");

        var saved = controller.save(A, request(KEY));
        var read = controller.read(A);

        var json = JsonMapper.builder().build();
        for (Object response : List.of(saved, read)) {
            String text = json.writeValueAsString(response);
            assertThat(text).doesNotContain(KEY).doesNotContain("sk-ant")
                    .doesNotContain(Base64.getEncoder().encodeToString(rows.stream()
                            .filter(AiConfiguration::hasKey).findFirst().orElseThrow().getEncryptedKey())
                            .substring(0, 20))
                    .contains("\"chaveCadastrada\":true").contains("\"chaveFinal\":\"x9Qa\"")
                    .contains("\"modoEfetivo\":\"API_KEY\"").contains("\"atualizadoPor\":\"pessoa.admin\"");
        }
    }

    @Test
    void catalogComesOutWithoutPublicKey() throws Exception {
        logIn("ADMIN");

        String text = JsonMapper.builder().build().writeValueAsString(controller.providers());

        assertThat(text).contains("\"codigo\":\"anthropic\"").contains("\"precoEntradaMilhaoUsd\":\"2.00\"")
                .contains("\"uso\":\"EMBEDDINGS\"").doesNotContain("BEGIN PUBLIC KEY").doesNotContain("chavePublica");
    }

    private static AiConfigurationRequest request(String key) {
        return new AiConfigurationRequest(AiMode.MCP_EXTERNO, new AiAssistantRequest(
                new AiAnswersRequest(AiMode.API_KEY, "anthropic", null, key, null),
                new AiEmbeddingsRequest(AiMode.LOCAL, "ollama-local", null)));
    }

    private static void logIn(String role) {
        Jwt jwt = new Jwt("t", Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "none"),
                Map.of("preferred_username", "pessoa." + role.toLowerCase(), "condominios", List.of(A.toString())));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)), "pessoa." + role.toLowerCase()));
    }
}
