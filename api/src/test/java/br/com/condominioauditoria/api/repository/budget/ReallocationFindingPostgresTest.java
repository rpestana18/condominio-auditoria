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
 * V12 on real PostgreSQL (temporary schema): one active reallocation per entry and budget version; reallocation and
 * finding are never deleted; the reallocation and finding trails are insert-only; the finding state is one of the
 * expected ones. Database as in {@link AccountMappingTrailPostgresTest}; without an accessible database, the test is
 * skipped.
 */
class ReallocationFindingPostgresTest {

    private static final String CONDOMINIUM = "6f1d2c1e-3b4a-4c8e-9a51-2815a0000001"; // piloto (V2)

    private final String url = env("BANCO_TESTE_URL", "jdbc:postgresql://localhost:5432/condominio");
    private final String username = env("BANCO_TESTE_USUARIO", "condominio");
    private final String password = env("BANCO_TESTE_SENHA", "condominio");
    private final String schema = "teste_realoc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
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
    void oneActiveReallocationPerEntryAndNothingIsDeleted() throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.execute("set search_path to " + schema);
            BaseRows b = baseRows(s);
            UUID first = insertReallocation(s, b);

            assertThatThrownBy(() -> insertReallocation(s, b)).isInstanceOf(SQLException.class)
                    .hasMessageContaining("uk_realocacao_ativa");
            // Undone, the same key can be reallocated again
            s.executeUpdate("update reallocation set undone_by = 'admin', undone_at = now() where id = '"
                    + first + "'");
            insertReallocation(s, b);
            assertThatThrownBy(() -> s.executeUpdate("delete from reallocation where id = '" + first + "'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("Nada é apagado");
            assertThatThrownBy(() -> s.executeUpdate("update reallocation set undone_at = null where id = '"
                    + first + "'")).isInstanceOf(SQLException.class).hasMessageContaining("ck_realocacao_desfeita");

            UUID event = UUID.randomUUID();
            s.executeUpdate("insert into reallocation_event (id, reallocation_id, condominium_id, action, username, "
                    + "occurred_at, detail)"
                    + " values ('" + event + "', '" + first + "', '" + CONDOMINIUM + "', 'REALOCADA', 'gestor', now(),"
                    + " 'teste')");
            assertThatThrownBy(() -> s.executeUpdate("update reallocation_event set username = 'outro'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("só de inserção");
            assertThatThrownBy(() -> s.executeUpdate("delete from reallocation_event"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("só de inserção");
        }
    }

    @Test
    void findingIsNotDeletedHasValidStatusAndInsertOnlyHistory() throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.execute("set search_path to " + schema);
            UUID finding = UUID.randomUUID();
            s.executeUpdate("insert into finding (id, condominium_id, rule, rule_version, severity, "
                    + "reference_month, target,"
                    + " description, status, created_at) values ('" + finding + "', '" + CONDOMINIUM + "',"
                    + " 'CONTA_SEM_LINHA_PO', '1', 'ATENCAO', date '2026-09-01', 'conta:8888', 'teste', 'ABERTO', "
                    + "now())");

            assertThat(s.executeUpdate("update finding set status = 'NAO_SE_APLICA_MAIS', condition_present = false,"
                    + " status_reason = 'de-para da conta 8888 confirmado por admin em 04/10/2026' where id = "
                    + "'" + finding
                    + "'")).isEqualTo(1);
            assertThatThrownBy(() -> s.executeUpdate("update finding set status = 'APAGADO'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("ck_achado_estado");
            assertThatThrownBy(() -> s.executeUpdate("delete from finding")).isInstanceOf(SQLException.class)
                    .hasMessageContaining("Nada é apagado");

            s.executeUpdate("insert into finding_event (id, finding_id, condominium_id, previous_status, new_status,"
                    + " condition_present, reason, username, occurred_at) values ('" + UUID.randomUUID() + "', "
                    + "'" + finding + "', '"
                    + CONDOMINIUM + "', 'ABERTO', 'NAO_SE_APLICA_MAIS', false, 'teste', 'admin', now())");
            assertThatThrownBy(() -> s.executeUpdate("update finding_event set reason = 'outro'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("só de inserção");
            assertThatThrownBy(() -> s.executeUpdate("delete from finding_event"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("só de inserção");
        }
    }

    private record BaseRows(UUID file, UUID budget, UUID line) {
    }

    private static BaseRows baseRows(Statement s) throws SQLException {
        UUID file = UUID.randomUUID();
        UUID budget = UUID.randomUUID();
        UUID line = UUID.randomUUID();
        s.executeUpdate("insert into source_file (id, condominium_id, category, original_name, path, sha256, "
                + "size_bytes,"
                + " status, uploaded_by, uploaded_at, processing_id) values ('" + file + "', '" + CONDOMINIUM
                + "', 'PO', 'po.pdf', 'c/po.pdf', '" + "a".repeat(64) + "', 1, 'CONCLUIDO', 'admin', now(), '"
                + UUID.randomUUID() + "')");
        s.executeUpdate("insert into budget (id, condominium_id, file_id, sha256, status,"
                + " rounding_tolerance, read_at) values ('" + budget + "', '" + CONDOMINIUM + "', '" + file
                + "', '" + "a".repeat(64) + "', 'CONFIRMADA', 0.01, now())");
        s.executeUpdate("insert into budget_line (id, budget_id, condominium_id, file_id, sha256, position, page, type,"
                + " printed_code, effective_code, description, previous_budgeted, budgeted) values ('" + line + "', '"
                + budget + "', '" + CONDOMINIUM + "', '" + file + "', '" + "a".repeat(64) + "', 1, 1, 'LINHA',"
                + " '1.7.9', '1.7.9', 'Material de pintura', 0, 2300.00)");
        return new BaseRows(file, budget, line);
    }

    private static UUID insertReallocation(Statement s, BaseRows b) throws SQLException {
        UUID id = UUID.randomUUID();
        s.executeUpdate("insert into reallocation (id, condominium_id, budget_id, entry_key, file_id, sha256,"
                + " page, position, date, account_code, memo, amount, budget_line_id, reallocated_by, reallocated_at)"
                + " values ('" + id + "', '" + CONDOMINIUM + "', '" + b.budget() + "', '" + "c".repeat(64) + "', '"
                + b.file() + "', '" + "a".repeat(64) + "', 3, 12, date '2026-09-09', '1064', 'Compra no cartão',"
                + " 250.00, '" + b.line() + "', 'gestor', now())");
        return id;
    }

    private static String env(String name, String defaults) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? defaults : v;
    }
}
