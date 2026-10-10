package br.com.condominioauditoria.api.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.api.grpc.client.AssistantClient;
import br.com.condominioauditoria.api.model.enums.AiMode;
import br.com.condominioauditoria.api.security.TestKeyPair;
import br.com.condominioauditoria.api.service.ai.AiConfigurationService.AnswersChange;
import br.com.condominioauditoria.api.service.ai.AiConfigurationService.Change;
import br.com.condominioauditoria.api.service.ai.AiConfigurationService.EmbeddingsChange;
import br.com.condominioauditoria.api.service.feature.FeatureService;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * AI configuration against a real PostgreSQL (only runs with BANCO_TESTE, e.g.
 * jdbc:postgresql://localhost:55433/condominio): Flyway up to V13, Hibernate validation, saving with the encrypted key
 * in bytea, insert-only audit trail (trigger recusar_alteracao_trilha of V7), uniqueness per condominium + feature +
 * function (with a null feature) and general mode constraints. The rag is fake (catalog and public key generated in the
 * test).
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "BANCO_TESTE", matches = "jdbc:postgresql:.+")
class AiConfigurationServicePostgresTest {

    private static final String KEY = "sk-ant-api03-ChaveDeTesteNaoReal-x9Qa";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        registry.add("spring.datasource.url", () -> System.getenv("BANCO_TESTE") + "?currentSchema=backend");
        registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> "false");
        registry.add("condominio.grpc.port", () -> "0");
        String folder = Files.createTempDirectory("dados-teste").toString();
        registry.add("condominio.storage.folder", () -> folder);
    }

    @MockitoBean
    AssistantClient rag;
    @Autowired
    AiConfigurationService service;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void catalog() {
        when(rag.listProviders(anyString()))
                .thenReturn(TestCatalog.response(TestKeyPair.publicPem(TestKeyPair.pair())));
    }

    @Test
    void defaultsWithoutRowsAndSaveEncryptsAndKeepsTrailWithoutKey() throws Exception {
        UUID created = newCondominium();
        assertThat(service.read(created).generalMode()).isEqualTo(AiMode.MCP_EXTERNO);
        assertThat(jdbc.queryForObject("select count(*) from ai_configuration where condominium_id = ?", Long.class,
                created)).isZero();

        service.save(created, new Change(AiMode.DESLIGADO, new AnswersChange(AiMode.API_KEY, "anthropic", null, KEY,
                false), new EmbeddingsChange(AiMode.DESLIGADO, null, null)), "admin", "Bearer t");

        var e = service.read(created);
        assertThat(e.generalMode()).isEqualTo(AiMode.DESLIGADO);
        assertThat(e.answers().effectiveMode()).isEqualTo(AiMode.API_KEY);
        assertThat(e.answers().keySuffix()).isEqualTo("x9Qa");
        assertThat(TestKeyPair.decrypt(e.answers().encryptedKey(), TestKeyPair.pair().getPrivate()))
                .isEqualTo(KEY);
        assertThat(e.embeddings().mode()).isEqualTo(AiMode.DESLIGADO);

        List<Map<String, Object>> trail = jdbc.queryForList(
                "select * from ai_configuration_event where condominium_id = ? order by function, feature nulls first",
                        created);
        assertThat(trail).hasSize(3);
        assertThat(trail).allSatisfy(row -> assertThat(row.values()).noneMatch(
                v -> v != null && v.toString().contains("sk-ant")));
        assertThat(trail).anySatisfy(row -> {
            assertThat(row.get("function")).isEqualTo("RESPOSTAS");
            assertThat(row.get("feature")).isEqualTo(FeatureService.ASSISTANT);
            assertThat(row.get("key_replaced")).isEqualTo(true);
            assertThat(row.get("key_suffix")).isEqualTo("x9Qa");
            assertThat(row.get("new_mode")).isEqualTo("API_KEY");
        });

        // Same request again (without resending the key): nothing changes, no event
        service.save(created, new Change(AiMode.DESLIGADO, new AnswersChange(AiMode.API_KEY, "anthropic", null, null,
                false), new EmbeddingsChange(AiMode.DESLIGADO, null, null)), "admin", "Bearer t");
        assertThat(jdbc.queryForObject("select count(*) from ai_configuration_event where condominium_id = ?",
                Long.class, created)).isEqualTo(3);
    }

    @Test
    void trailRejectsUpdateDeleteAndTruncate() {
        UUID created = newCondominium();
        service.save(created, new Change(AiMode.DESLIGADO, new AnswersChange(null, null, null, null, false),
                new EmbeddingsChange(AiMode.LOCAL, "ollama-local", null)), "admin", "Bearer t");

        assertThatThrownBy(() -> jdbc.update("update ai_configuration_event set username = 'x' where condominium_id "
                + "= ?",
                created)).isInstanceOf(DataAccessException.class).hasMessageContaining("só de inserção");
        assertThatThrownBy(() -> jdbc.update("delete from ai_configuration_event where condominium_id = ?", created))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("só de inserção");
        assertThatThrownBy(() -> jdbc.execute("truncate ai_configuration_event"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("só de inserção");
    }

    @Test
    void oneRowPerCondominiumFeatureAndFunctionEvenWithNullFeature() {
        UUID created = newCondominium();
        String general = "insert into ai_configuration (id, condominium_id, feature, function, mode, updated_by,"
                + " updated_at) values (gen_random_uuid(), ?, null, 'RESPOSTAS', 'DESLIGADO', 'admin', now())";
        jdbc.update(general, created);
        assertThatThrownBy(() -> jdbc.update(general, created)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void generalModeHasNoProviderNorKeyAndKeyWithoutSuffixIsRejected() {
        UUID created = newCondominium();
        assertThatThrownBy(() -> jdbc.update("insert into ai_configuration (id, condominium_id, feature, function, "
                + "mode,"
                + " provider, updated_by, updated_at) values (gen_random_uuid(), ?, null, 'RESPOSTAS',"
                + " 'API_KEY', 'anthropic', 'admin', now())", created)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("insert into ai_configuration (id, condominium_id, feature, function, "
                + "mode,"
                + " encrypted_key, updated_by, updated_at) values (gen_random_uuid(), ?, 'ASSISTENTE',"
                + " 'RESPOSTAS', 'API_KEY', '\\x01'::bytea, 'admin', now())", created))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("insert into ai_configuration (id, condominium_id, feature, function, "
                + "mode,"
                + " updated_by, updated_at) values (gen_random_uuid(), ?, 'ASSISTENTE', 'EMBEDDINGS', null,"
                + " 'admin', now())", created)).isInstanceOf(DataAccessException.class);
    }

    private UUID newCondominium() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into condominium (id, name) values (?, ?)", id, "Teste IA " + id);
        return id;
    }
}
