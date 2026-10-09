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

            assertThatThrownBy(() -> s.executeUpdate("update evento_rubrica set usuario = 'outro' where id = '"
                    + ids.event() + "'")).isInstanceOf(SQLException.class).hasMessageContaining("só de inserção")
                    .hasMessageContaining("UPDATE");
            assertThatThrownBy(() -> s.executeUpdate("delete from evento_rubrica where id = '" + ids.event() + "'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("só de inserção")
                    .hasMessageContaining("DELETE");
            try (var r = s.executeQuery("select usuario from evento_rubrica where id = '" + ids.event() + "'")) {
                assertThat(r.next()).isTrue();
                assertThat(r.getString(1)).isEqualTo("admin");
            }
            // The current state accepts changes; only the trail is immutable
            assertThat(s.executeUpdate("update linha_rubrica set estado = 'CONFIRMADO'")).isEqualTo(1);
            assertThat(s.executeUpdate("update rubrica set nome = 'Sindicatura'")).isEqualTo(1);
        }
    }

    @Test
    void eachLineHasOneItemAndValidStatus() throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.execute("set search_path to " + schema);
            Ids ids = insert(s);
            assertThatThrownBy(() -> s.executeUpdate(lineItemSql(ids, "SUGERIDO")))
                    .isInstanceOf(SQLException.class).hasMessageContaining("uk_linha_rubrica");
            s.executeUpdate("delete from linha_rubrica");
            assertThatThrownBy(() -> s.executeUpdate(lineItemSql(ids, "TALVEZ")))
                    .isInstanceOf(SQLException.class).hasMessageContaining("ck_linha_rubrica_estado");
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
        s.executeUpdate("insert into arquivo (id, condominio_id, categoria, nome_original, caminho, sha256, tamanho_bytes,"
                + " status, enviado_por, enviado_em, processamento_id) values ('" + file + "', '" + CONDOMINIUM
                + "', 'PO', 'po.pdf', 'c/po.pdf', '" + "a".repeat(64) + "', 1, 'CONCLUIDO', 'admin', now(), '"
                + UUID.randomUUID() + "')");
        s.executeUpdate("insert into previsao_orcamentaria (id, condominio_id, arquivo_id, sha256, estado,"
                + " tolerancia_arredondamento, lida_em) values ('" + budget + "', '" + CONDOMINIUM + "', '" + file
                + "', '" + "a".repeat(64) + "', 'CONFIRMADA', 0.01, now())");
        s.executeUpdate("insert into linha_po (id, previsao_id, condominio_id, arquivo_id, sha256, ordem, pagina, tipo,"
                + " codigo_impresso, codigo_efetivo, conta, descricao, orcado_anterior, orcado) values ('" + line
                + "', '" + budget + "', '" + CONDOMINIUM + "', '" + file + "', '" + "a".repeat(64)
                + "', 1, 1, 'LINHA', '1.3.20', '1.3.20', '1682 - Sindicatura Profissional', 'Obm', 17195.00, 8000.00)");
        s.executeUpdate("insert into rubrica (id, condominio_id, nome, grupo_codigo, linha_origem_id, criada_por,"
                + " criada_em) values ('" + item + "', '" + CONDOMINIUM + "', '1682 - Sindicatura Profissional', '1.3', '"
                + line + "', 'admin', now())");
        Ids ids = new Ids(budget, line, item, event);
        s.executeUpdate(lineItemSql(ids, "SUGERIDO"));
        s.executeUpdate("insert into evento_rubrica (id, condominio_id, previsao_id, linha_po_id, linha_codigo, acao,"
                + " usuario, em, rubrica_nova_id, rubrica_nova, estado_novo, origem) values ('" + event + "', '"
                + CONDOMINIUM + "', '" + budget + "', '" + line + "', '1.3.20', 'SUGERIDO', 'admin', now(), '" + item
                + "', '1682 - Sindicatura Profissional', 'SUGERIDO', 'CONTA_PO')");
        return ids;
    }

    private static String lineItemSql(Ids ids, String status) {
        return "insert into linha_rubrica (id, condominio_id, previsao_id, linha_po_id, rubrica_id, estado, origem,"
                + " atualizado_por, atualizado_em) values ('" + UUID.randomUUID() + "', '" + CONDOMINIUM + "', '"
                + ids.budget() + "', '" + ids.line() + "', '" + ids.item() + "', '" + status
                + "', 'CONTA_PO', 'admin', now())";
    }

    private static String env(String name, String defaults) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? defaults : v;
    }
}
