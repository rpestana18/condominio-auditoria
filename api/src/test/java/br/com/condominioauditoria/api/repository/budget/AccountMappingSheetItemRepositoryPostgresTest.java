package br.com.condominioauditoria.api.repository.budget;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.api.model.budget.AccountMappingSheetItem;
import br.com.condominioauditoria.api.model.budget.MappingTarget;
import br.com.condominioauditoria.api.model.enums.MappingTargetType;
import java.nio.file.Files;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Q51: the condominium's stored suggestion sheet in the real database (V20 and the JPA mapping): a new sheet replaces
 * the stored one in the same transaction, without breaking the one-line-per-account key. Runs only with BANCO_TESTE.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "BANCO_TESTE", matches = "jdbc:postgresql:.+")
class AccountMappingSheetItemRepositoryPostgresTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        registry.add("spring.datasource.url", () -> System.getenv("BANCO_TESTE") + "?currentSchema=backend");
        registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> "false");
        registry.add("condominio.grpc.port", () -> "0");
        String folder = Files.createTempDirectory("dados-teste").toString();
        registry.add("condominio.storage.folder", () -> folder);
    }

    @Autowired
    AccountMappingSheetItemRepository items;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PlatformTransactionManager transactions;
    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void newSheetReplacesTheStoredOne() {
        UUID condominium = UUID.randomUUID();
        jdbc.update("insert into condominium (id, name) values (?, ?)", condominium, "Teste " + condominium);
        TransactionTemplate tx = new TransactionTemplate(transactions);
        Instant now = Instant.now();
        UUID line = UUID.randomUUID();
        tx.executeWithoutResult(s -> {
            items.save(new AccountMappingSheetItem(condominium, "1621",
                    new MappingTarget(MappingTargetType.BUDGET_LINE, line, "1.7.8", "Material hidráulico", null),
                    "mapa.csv", "a".repeat(64), 2, "admin", now));
            items.save(new AccountMappingSheetItem(condominium, "1324",
                    MappingTarget.special(MappingTargetType.ADJUSTMENT, "estorno"), "mapa.csv", "a".repeat(64), 3,
                    "admin", now));
        });

        tx.executeWithoutResult(s -> {
            assertThat(items.deleteByCondominiumId(condominium)).isEqualTo(2);
            items.save(new AccountMappingSheetItem(condominium, "1621",
                    new MappingTarget(MappingTargetType.BUDGET_LINE, line, "1.3.20", null, null), "mapa2.csv",
                    "b".repeat(64), 2, "admin2", now));
        });

        assertThat(items.findByCondominiumIdOrderByAccountCode(condominium)).singleElement().satisfies(i -> {
            assertThat(i.getTargetLineCode()).isEqualTo("1.3.20");
            assertThat(i.getFileName()).isEqualTo("mapa2.csv");
            assertThat(i.getLoadedBy()).isEqualTo("admin2");
        });
    }
}
