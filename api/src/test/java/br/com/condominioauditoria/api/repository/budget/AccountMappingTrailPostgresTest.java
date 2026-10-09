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

            assertThatThrownBy(() -> s.executeUpdate("update evento_depara set usuario = 'outro' where id = '" + event
                    + "'")).isInstanceOf(SQLException.class).hasMessageContaining("só de inserção")
                    .hasMessageContaining("UPDATE");
            assertThatThrownBy(() -> s.executeUpdate("delete from evento_depara where id = '" + event + "'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("só de inserção")
                    .hasMessageContaining("DELETE");

            try (var r = s.executeQuery("select usuario from evento_depara where id = '" + event + "'")) {
                assertThat(r.next()).isTrue();
                assertThat(r.getString(1)).isEqualTo("admin");
            }
            // The mapping table (current state) accepts changes; only the trail is immutable
            assertThat(s.executeUpdate("update depara_conta set estado = 'CONFIRMADO'")).isEqualTo(1);
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
        s.executeUpdate("insert into arquivo (id, condominio_id, categoria, nome_original, caminho, sha256, tamanho_bytes,"
                + " status, enviado_por, enviado_em, processamento_id) values ('" + file + "', '" + condominium
                + "', 'PO', 'po.pdf', 'c/po.pdf', '" + "a".repeat(64) + "', 1, 'CONCLUIDO', 'admin', now(), '"
                + UUID.randomUUID() + "')");
        s.executeUpdate("insert into previsao_orcamentaria (id, condominio_id, arquivo_id, sha256, estado,"
                + " tolerancia_arredondamento, lida_em) values ('" + budget + "', '" + condominium + "', '" + file
                + "', '" + "a".repeat(64) + "', 'CONFIRMADA', 0.01, now())");
        s.executeUpdate(mappingSql(budget.toString(), "1621", "AJUSTE", "null"));
        s.executeUpdate("insert into evento_depara (id, condominio_id, previsao_id, conta_codigo, acao, usuario, em,"
                + " tipo_destino_novo, destino_novo, estado_novo, origem) values ('" + event + "', '" + condominium
                + "', '" + budget + "', '1621', 'SUGERIDO', 'admin', now(), 'AJUSTE', 'AJUSTE', 'SUGERIDO', 'ADMIN')");
        return event;
    }

    private static String budgetId(Statement s) throws SQLException {
        try (var r = s.executeQuery("select id from previsao_orcamentaria")) {
            r.next();
            return r.getString(1);
        }
    }

    private static String mappingSql(String budget, String account, String type, String line) {
        return "insert into depara_conta (id, condominio_id, previsao_id, conta_codigo, tipo_destino, linha_po_id, estado,"
                + " origem, atualizado_por, atualizado_em) values ('" + UUID.randomUUID()
                + "', '6f1d2c1e-3b4a-4c8e-9a51-2815a0000001', '" + budget + "', '" + account + "', '" + type + "', "
                + line + ", 'SUGERIDO', 'ADMIN', 'admin', now())";
    }

    private static String env(String name, String defaults) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? defaults : v;
    }
}
