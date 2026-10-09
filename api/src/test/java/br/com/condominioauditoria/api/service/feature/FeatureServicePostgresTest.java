package br.com.condominioauditoria.api.service.feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.condominioauditoria.api.model.enums.UsageFunction;
import br.com.condominioauditoria.api.model.usage.UsageTotal;
import br.com.condominioauditoria.api.service.usage.UsageService;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Against a real PostgreSQL (runs only with the BANCO_TESTE variable, e.g.
 * jdbc:postgresql://localhost:55432/condominio): Flyway up to V11, Hibernate validation, pilot enabled, insert-only
 * trail and usage (triggers), periods and the usage summary through the native query. Uses new condominiums on every
 * run, because the trail cannot be deleted.
 *
 * Without the variable, the test is skipped (the build does not depend on a database).
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "BANCO_TESTE", matches = "jdbc:postgresql:.+")
class FeatureServicePostgresTest {

    private static final UUID PILOT = UUID.fromString("6f1d2c1e-3b4a-4c8e-9a51-2815a0000001");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        registry.add("spring.datasource.url", () -> System.getenv("BANCO_TESTE") + "?currentSchema=backend");
        registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> "false");
        registry.add("condominio.grpc.port", () -> "0");
        String folder = Files.createTempDirectory("dados-teste").toString();
        registry.add("condominio.storage.folder", () -> folder);
    }

    @Autowired
    FeatureService features;
    @Autowired
    UsageService usage;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void pilotStartsEnabledWithTheOnboardingEvent() {
        assertThat(features.isEnabled(PILOT, FeatureService.ASSISTANT)).isTrue();
        assertThat(features.events(PILOT, FeatureService.ASSISTANT)).first().satisfies(e -> {
            assertThat(e.isEnabledBefore()).isFalse();
            assertThat(e.isEnabledAfter()).isTrue();
            assertThat(e.getReason()).isEqualTo("Implantação do piloto");
        });
        assertThat(features.periods(PILOT, FeatureService.ASSISTANT)).first()
                .satisfies(p -> assertThat(p.end()).isNull());
    }

    @Test
    void newCondominiumStartsDisabledAndEnableDisableFormsAPeriod() {
        UUID created = newCondominium();
        assertThat(features.isEnabled(created, FeatureService.ASSISTANT)).isFalse();

        features.change(created, FeatureService.ASSISTANT, true, "Contrato assinado", "admin");
        assertThat(features.isEnabled(created, FeatureService.ASSISTANT)).isTrue();
        features.change(created, FeatureService.ASSISTANT, true, "repetido", "admin"); // no change, no event
        features.change(created, FeatureService.ASSISTANT, false, "Fim do teste", "outro.admin");

        assertThat(features.events(created, FeatureService.ASSISTANT)).hasSize(2);
        assertThat(features.periods(created, FeatureService.ASSISTANT)).singleElement().satisfies(p -> {
            assertThat(p.enabledBy()).isEqualTo("admin");
            assertThat(p.disabledBy()).isEqualTo("outro.admin");
            assertThat(p.end()).isAfterOrEqualTo(p.start());
        });
        assertThat(features.enabledCodes(created)).isEmpty();
    }

    @Test
    void trailAndUsageRejectUpdateDeleteAndTruncate() {
        UUID created = newCondominium();
        features.change(created, FeatureService.ASSISTANT, true, "Contrato assinado", "admin");
        usage.recordMcpCall(created, "conselheiro", true);

        assertThatThrownBy(() -> jdbc.update("update evento_modulo set motivo = 'outro' where condominio_id = ?",
                created))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("só aceita inclusão");
        assertThatThrownBy(() -> jdbc.update("delete from evento_modulo where condominio_id = ?", created))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("só aceita inclusão");
        assertThatThrownBy(() -> jdbc.execute("truncate evento_modulo"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("só aceita inclusão");
        assertThatThrownBy(() -> jdbc.update("update uso_modulo set usuario = 'x' where condominio_id = ?", created))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("só aceita inclusão");
        assertThatThrownBy(() -> jdbc.update("delete from uso_modulo where condominio_id = ?", created))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("só aceita inclusão");
        assertThat(jdbc.queryForObject("select count(*) from evento_modulo where condominio_id = ?", Long.class,
                created))
                .isEqualTo(1);
    }

    @Test
    void databaseAcceptsNullReasonAndRejectsBlankReasonAndUnknownFunction() {
        UUID created = newCondominium();
        assertThatThrownBy(() -> jdbc.update("insert into evento_modulo values (gen_random_uuid(), ?, 'ASSISTENTE',"
                + " false, true, 'admin', now(), '   ')", created)).isInstanceOf(DataAccessException.class);
        features.change(created, FeatureService.ASSISTANT, true, null, "admin");
        assertThat(features.events(created, FeatureService.ASSISTANT)).singleElement()
                .satisfies(e -> assertThat(e.getReason()).isNull());
        assertThatThrownBy(() -> jdbc.update("insert into uso_modulo (id, condominio_id, modulo, funcao, quando)"
                + " values (gen_random_uuid(), ?, 'ASSISTENTE', 'chat', now())", created))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void usageSummaryByFunctionAndByMonth() {
        UUID created = newCondominium();
        usage.recordIndexing(created, 7, "bge-m3");
        usage.recordIndexing(created, 3, null);
        usage.recordMcpCall(created, "conselheiro", true);
        usage.recordMcpCall(created, "conselheiro", false);
        LocalDate today = LocalDate.now(UsageService.ZONE);

        var summary = usage.summary(created, today.withDayOfMonth(1), today);

        assertThat(summary.byFunction()).containsExactly(
                new UsageTotal(null, FeatureService.ASSISTANT, UsageFunction.CHAMADA_MCP, 2, 0, 0, 0, 0),
                new UsageTotal(null, FeatureService.ASSISTANT, UsageFunction.INDEXACAO, 2, 0, 0, 2, 10));
        assertThat(summary.byMonth()).extracting(UsageTotal::month).containsOnly(today.toString().substring(0, 7));
        assertThat(usage.summary(created, today.minusYears(1), today.minusYears(1)).byMonth()).isEmpty();
    }

    /** First enable by two ADMINs at the same time: no error, a single row and a single event. */
    @Test
    void concurrentFirstEnableNeitherFailsNorDuplicatesTheEvent() throws Exception {
        UUID created = newCondominium();
        int requests = 8;
        var startSignal = new CountDownLatch(1);
        try (ExecutorService threads = Executors.newFixedThreadPool(requests)) {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                String username = "admin" + i;
                results.add(threads.submit(() -> {
                    startSignal.await();
                    return features.change(created, FeatureService.ASSISTANT, true, "ao mesmo tempo", username);
                }));
            }
            startSignal.countDown();
            for (Future<?> r : results) {
                r.get(30, TimeUnit.SECONDS); // throws if any request failed
            }
        }
        assertThat(features.isEnabled(created, FeatureService.ASSISTANT)).isTrue();
        assertThat(features.events(created, FeatureService.ASSISTANT)).hasSize(1);
    }

    private UUID newCondominium() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into condominio (id, nome) values (?, ?)", id, "Teste " + id);
        return id;
    }
}
