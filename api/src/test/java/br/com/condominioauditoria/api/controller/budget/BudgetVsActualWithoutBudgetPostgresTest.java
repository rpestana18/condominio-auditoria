package br.com.condominioauditoria.api.controller.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The calls of the "Previsto × realizado" screen for a condominium without any budget (PO), through the real HTTP
 * stack (filters, error dispatch). Runs only with BANCO_TESTE, like the other PostgreSQL tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "BANCO_TESTE", matches = "jdbc:postgresql:.+")
class BudgetVsActualWithoutBudgetPostgresTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        registry.add("spring.datasource.url", () -> System.getenv("BANCO_TESTE") + "?currentSchema=backend");
        registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> "false");
        registry.add("condominio.grpc.port", () -> "0");
        String folder = Files.createTempDirectory("dados-teste").toString();
        registry.add("condominio.storage.folder", () -> folder);
    }

    @LocalServerPort
    int port;
    @Autowired
    JdbcTemplate jdbc;
    @MockitoBean
    JwtDecoder jwtDecoder;

    private final HttpClient http = HttpClient.newHttpClient();
    private UUID condominium;

    @BeforeEach
    void setUp() {
        condominium = UUID.randomUUID();
        jdbc.update("insert into condominium (id, name) values (?, ?)", condominium, "Teste " + condominium);
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("user")
                .claim("preferred_username", "usuario")
                .claim("realm_access", Map.of("roles", List.of("USER")))
                .claim("condominiums", List.of(condominium.toString()))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600)).build();
        when(jwtDecoder.decode(anyString())).thenReturn(jwt);
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port
                        + "/api/condominiums/" + condominium + path))
                .header("Authorization", "Bearer token").GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void screenCallsWithoutAnyBudgetAnswerWithTheEmptyState() throws Exception {
        HttpResponse<String> cumulative = get("/budget-vs-actual?period=cumulative");
        assertThat(cumulative.statusCode()).isEqualTo(200);
        assertThat(cumulative.body()).contains("\"status\":\"NO_BUDGET\"").contains("\"budget\":null");
        for (String path : List.of("/fiscal-years", "/budgets", "/funds")) {
            HttpResponse<String> response = get(path);
            assertThat(response.statusCode()).as(path).isEqualTo(200);
            assertThat(response.body()).as(path).isEqualTo("[]");
        }
    }

    /**
     * The defect: the home card linked to ?periodo=acumulado and the screen sent period=acumulado, which the API
     * refuses since it went to English (BudgetVsActualQueryService.period). The 400 now carries the reason.
     */
    @Test
    void oldPortuguesePeriodIsRefusedWithTheReasonInDetail() throws Exception {
        HttpResponse<String> response = get("/budget-vs-actual?period=acumulado");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                t -> assertThat(t).contains("application/problem+json"));
        assertThat(response.body())
                .contains("\"detail\":\"Período deve ser AAAA-MM (ex.: 2026-09) ou \\\"cumulative\\\": acumulado\"")
                .contains("\"title\":\"Requisição inválida\"")
                .doesNotContain("trace").doesNotContain("Exception");
    }

    @Test
    void errorsRaisedBySpringItselfAlsoCarryAPortugueseDetail() throws Exception {
        HttpResponse<String> missing = get("/budget-vs-actual");
        assertThat(missing.statusCode()).isEqualTo(400);
        assertThat(missing.body()).contains("\"detail\":\"Parâmetro obrigatório ausente: period\"");

        HttpResponse<String> badUuid = get("/budget-vs-actual?period=cumulative&budget=x");
        assertThat(badUuid.statusCode()).isEqualTo(400);
        assertThat(badUuid.body()).contains("\"detail\":\"Valor inválido para o parâmetro budget\"")
                .doesNotContain("Exception");

        HttpResponse<String> unknownBudget = get("/budget-vs-actual?period=cumulative&budget=" + UUID.randomUUID());
        assertThat(unknownBudget.statusCode()).isEqualTo(404);
        assertThat(unknownBudget.body()).contains("\"detail\":\"PO não encontrada\"");
    }

    @Test
    void otherCondominiumIsRefusedWith403AndDetail() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/condominiums/"
                        + UUID.randomUUID() + "/budget-vs-actual?period=cumulative"))
                .header("Authorization", "Bearer token").GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("\"detail\":\"Seu perfil não tem permissão");
    }
}
