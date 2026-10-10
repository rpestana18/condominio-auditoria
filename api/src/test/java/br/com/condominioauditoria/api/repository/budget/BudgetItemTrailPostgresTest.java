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
 * RF-11.7 and ADR 0005, Decision 1: the budget item trail is insert-only, and the database rejects update and delete
 * (V14 trigger), not the code. One item per budget line. Runs the real migrations in a temporary PostgreSQL schema and
 * drops it at the end. Without a reachable database, the test is skipped (variables as in
 * AccountMappingTrailPostgresTest).
 */
class BudgetItemTrailPostgresTest {

    private static final String CONDOMINIUM = "6f1d2c1e-3b4a-4c8e-9a51-2815a0000001"; // piloto (V2)

    private final String url = env("BANCO_TESTE_URL", "jdbc:postgresql://localhost:5432/condominio");
    private final String username = env("BANCO_TESTE_USUARIO", "condominio");
    private final String password = env("BANCO_TESTE_SENHA", "condominio");
    private final String schema = "teste_rubrica_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
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
    void updateAndDeleteOnItemTrailAreRejectedByDatabase() throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.execute("set search_path to " + schema);
            Ids ids = insert(s);

            assertThatThrownBy(() -> s.executeUpdate("update budget_item_event set username = 'outro' where id = '"
                    + ids.event() + "'")).isInstanceOf(SQLException.class).hasMessageContaining("só de inserção")
                    .hasMessageContaining("UPDATE");
            assertThatThrownBy(() -> s.executeUpdate("delete from budget_item_event where id = '" + ids.event() + "'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("só de inserção")
                    .hasMessageContaining("DELETE");
            try (var r = s.executeQuery("select username from budget_item_event where id = '" + ids.event() + "'")) {
                assertThat(r.next()).isTrue();
                assertThat(r.getString(1)).isEqualTo("admin");
            }
            // The current state accepts changes; only the trail is immutable
            assertThat(s.executeUpdate("update budget_line_item set status = 'CONFIRMED'")).isEqualTo(1);
            assertThat(s.executeUpdate("update budget_item set name = 'Sindicatura'")).isEqualTo(1);
        }
    }

    @Test
    void eachLineHasOneItemAndValidStatus() throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.execute("set search_path to " + schema);
            Ids ids = insert(s);
            assertThatThrownBy(() -> s.executeUpdate(lineItemSql(ids, "SUGGESTED")))
                    .isInstanceOf(SQLException.class).hasMessageContaining("uk_linha_rubrica");
            s.executeUpdate("delete from budget_line_item");
            assertThatThrownBy(() -> s.executeUpdate(lineItemSql(ids, "TALVEZ")))
                    .isInstanceOf(SQLException.class).hasMessageContaining("ck_budget_line_item_status");
        }
    }

    private record Ids(UUID budget, UUID line, UUID item, UUID event) {
    }

    private static Ids insert(Statement s) throws SQLException {
        UUID file = UUID.randomUUID();
        UUID budget = UUID.randomUUID();
        UUID line = UUID.randomUUID();
        UUID item = UUID.randomUUID();
        UUID event = UUID.randomUUID();
        s.executeUpdate("insert into source_file (id, condominium_id, category, original_name, path, sha256, "
                + "size_bytes,"
                + " status, uploaded_by, uploaded_at, processing_id) values ('" + file + "', '" + CONDOMINIUM
                + "', 'PO', 'po.pdf', 'c/po.pdf', '" + "a".repeat(64) + "', 1, 'COMPLETED', 'admin', now(), '"
                + UUID.randomUUID() + "')");
        s.executeUpdate("insert into budget (id, condominium_id, file_id, sha256, status,"
                + " rounding_tolerance, read_at) values ('" + budget + "', '" + CONDOMINIUM + "', '" + file
                + "', '" + "a".repeat(64) + "', 'CONFIRMED', 0.01, now())");
        s.executeUpdate("insert into budget_line (id, budget_id, condominium_id, file_id, sha256, position, page, type,"
                + " printed_code, effective_code, account, description, previous_budgeted, budgeted) values ('" + line
                + "', '" + budget + "', '" + CONDOMINIUM + "', '" + file + "', '" + "a".repeat(64)
                + "', 1, 1, 'LINE', '1.3.20', '1.3.20', '1682 - Sindicatura Profissional', 'Obm', 17195.00, 8000.00)");
        s.executeUpdate("insert into budget_item (id, condominium_id, name, group_code, source_line_id, created_by,"
                + " created_at) values ('" + item + "', '" + CONDOMINIUM + "', '1682 - Sindicatura Profissional', "
                + "'1.3', '"
                + line + "', 'admin', now())");
        Ids ids = new Ids(budget, line, item, event);
        s.executeUpdate(lineItemSql(ids, "SUGGESTED"));
        s.executeUpdate("insert into budget_item_event (id, condominium_id, budget_id, budget_line_id, line_code, "
                + "action,"
                + " username, occurred_at, new_item_id, new_item, new_status, source) values ('" + event + "', '"
                + CONDOMINIUM + "', '" + budget + "', '" + line + "', '1.3.20', 'SUGGESTED', 'admin', now(), '" + item
                + "', '1682 - Sindicatura Profissional', 'SUGGESTED', 'BUDGET_ACCOUNT')");
        return ids;
    }

    private static String lineItemSql(Ids ids, String status) {
        return "insert into budget_line_item (id, condominium_id, budget_id, budget_line_id, budget_item_id, "
                + "status, source,"
                + " updated_by, updated_at) values ('" + UUID.randomUUID() + "', '" + CONDOMINIUM + "', '"
                + ids.budget() + "', '" + ids.line() + "', '" + ids.item() + "', '" + status
                + "', 'BUDGET_ACCOUNT', 'admin', now())";
    }

    private static String env(String name, String defaults) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? defaults : v;
    }
}
