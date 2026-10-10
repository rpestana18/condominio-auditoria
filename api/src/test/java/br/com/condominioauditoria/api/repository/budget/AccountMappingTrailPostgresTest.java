package br.com.condominioauditoria.api.repository.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * RF-03.1.4 and ADR 0004, Decision 3: the account mapping trail is insert-only, and the database rejects update and
 * delete (V8 trigger), not the code. Runs the real migrations in a temporary PostgreSQL schema and drops it at the end.
 *
 * <p>Database: variables BANCO_TESTE_URL, BANCO_TESTE_USUARIO and BANCO_TESTE_SENHA, or the application.yml default
 * (localhost:5432/condominio, user condominio). Without a reachable database, the test is skipped.
 */
class AccountMappingTrailPostgresTest {

    private final String url = env("BANCO_TESTE_URL", "jdbc:postgresql://localhost:5432/condominio");
    private final String username = env("BANCO_TESTE_USUARIO", "condominio");
    private final String password = env("BANCO_TESTE_SENHA", "condominio");
    private final String schema = "teste_trilha_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private Connection connection;

    @BeforeEach
    void migrate() {
        try {
            connection = DriverManager.getConnection(url, username, password);
        } catch (SQLException e) {
            assumeTrue(false, "PostgreSQL de teste indisponível: " + e.getMessage());
        }
        Flyway.configure().dataSource(url, username, password).schemas(schema).defaultSchema(schema)
                .createSchemas(true).load().migrate();
    }

    @AfterEach
    void clean() throws SQLException {
        if (connection != null) {
            try (Statement s = connection.createStatement()) {
                s.execute("drop schema if exists " + schema + " cascade");
            }
            connection.close();
        }
    }

    @Test
    void updateAndDeleteOnMappingTrailAreRejectedByDatabase() throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.execute("set search_path to " + schema);
            UUID event = insertEvent(s);

            assertThatThrownBy(() -> s.executeUpdate(
                    "update account_mapping_event set username = 'outro' where id = '" + event + "'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("só de inserção")
                    .hasMessageContaining("UPDATE");
            assertThatThrownBy(() -> s.executeUpdate("delete from account_mapping_event where id = '" + event + "'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("só de inserção")
                    .hasMessageContaining("DELETE");

            try (var r = s.executeQuery("select username from account_mapping_event where id = '" + event + "'")) {
                assertThat(r.next()).isTrue();
                assertThat(r.getString(1)).isEqualTo("admin");
            }
            // The mapping table (current state) accepts changes; only the trail is immutable
            assertThat(s.executeUpdate("update account_mapping set status = 'CONFIRMADO'")).isEqualTo(1);
        }
    }

    @Test
    void mappingHasOneTargetPerAccountAndConsistentTarget() throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.execute("set search_path to " + schema);
            insertEvent(s);
            String budget = budgetId(s);
            assertThatThrownBy(() -> s.executeUpdate(mappingSql(budget, "1621", "AJUSTE", "null")))
                    .isInstanceOf(SQLException.class).hasMessageContaining("uk_depara_conta");
            assertThatThrownBy(() -> s.executeUpdate(mappingSql(budget, "1622", "LINHA_PO", "null")))
                    .isInstanceOf(SQLException.class).hasMessageContaining("ck_depara_destino");
        }
    }

    private static UUID insertEvent(Statement s) throws SQLException {
        String condominium = "6f1d2c1e-3b4a-4c8e-9a51-2815a0000001"; // piloto (V2)
        UUID file = UUID.randomUUID();
        UUID budget = UUID.randomUUID();
        UUID event = UUID.randomUUID();
        s.executeUpdate("insert into source_file (id, condominium_id, category, original_name, path, sha256, "
                + "size_bytes,"
                + " status, uploaded_by, uploaded_at, processing_id) values ('" + file + "', '" + condominium
                + "', 'PO', 'po.pdf', 'c/po.pdf', '" + "a".repeat(64) + "', 1, 'CONCLUIDO', 'admin', now(), '"
                + UUID.randomUUID() + "')");
        s.executeUpdate("insert into budget (id, condominium_id, file_id, sha256, status,"
                + " rounding_tolerance, read_at) values ('" + budget + "', '" + condominium + "', '" + file
                + "', '" + "a".repeat(64) + "', 'CONFIRMADA', 0.01, now())");
        s.executeUpdate(mappingSql(budget.toString(), "1621", "AJUSTE", "null"));
        s.executeUpdate("insert into account_mapping_event (id, condominium_id, budget_id, account_code, action, "
                + "username, occurred_at,"
                + " new_target_type, new_target, new_status, source) values ('" + event + "', '" + condominium
                + "', '" + budget + "', '1621', 'SUGERIDO', 'admin', now(), 'AJUSTE', 'AJUSTE', 'SUGERIDO', 'ADMIN')");
        return event;
    }

    private static String budgetId(Statement s) throws SQLException {
        try (var r = s.executeQuery("select id from budget")) {
            r.next();
            return r.getString(1);
        }
    }

    private static String mappingSql(String budget, String account, String type, String line) {
        return "insert into account_mapping (id, condominium_id, budget_id, account_code, target_type, "
                + "budget_line_id, status,"
                + " source, updated_by, updated_at) values ('" + UUID.randomUUID()
                + "', '6f1d2c1e-3b4a-4c8e-9a51-2815a0000001', '" + budget + "', '" + account + "', '" + type + "', "
                + line + ", 'SUGERIDO', 'ADMIN', 'admin', now())";
    }

    private static String env(String name, String defaults) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? defaults : v;
    }
}
