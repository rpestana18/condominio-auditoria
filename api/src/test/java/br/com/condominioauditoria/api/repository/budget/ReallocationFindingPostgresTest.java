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
            s.executeUpdate("update realocacao set desfeita_por = 'admin', desfeita_em = now() where id = '"
                    + first + "'");
            insertReallocation(s, b);
            assertThatThrownBy(() -> s.executeUpdate("delete from realocacao where id = '" + first + "'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("Nada é apagado");
            assertThatThrownBy(() -> s.executeUpdate("update realocacao set desfeita_em = null where id = '"
                    + first + "'")).isInstanceOf(SQLException.class).hasMessageContaining("ck_realocacao_desfeita");

            UUID event = UUID.randomUUID();
            s.executeUpdate("insert into evento_realocacao (id, realocacao_id, condominio_id, acao, usuario, em, detalhe)"
                    + " values ('" + event + "', '" + first + "', '" + CONDOMINIUM + "', 'REALOCADA', 'gestor', now(),"
                    + " 'teste')");
            assertThatThrownBy(() -> s.executeUpdate("update evento_realocacao set usuario = 'outro'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("só de inserção");
            assertThatThrownBy(() -> s.executeUpdate("delete from evento_realocacao"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("só de inserção");
        }
    }

    @Test
    void findingIsNotDeletedHasValidStatusAndInsertOnlyHistory() throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.execute("set search_path to " + schema);
            UUID finding = UUID.randomUUID();
            s.executeUpdate("insert into achado (id, condominio_id, regra, versao_regra, severidade, competencia, alvo,"
                    + " descricao, estado, criado_em) values ('" + finding + "', '" + CONDOMINIUM + "',"
                    + " 'CONTA_SEM_LINHA_PO', '1', 'ATENCAO', date '2026-09-01', 'conta:8888', 'teste', 'ABERTO', now())");

            assertThat(s.executeUpdate("update achado set estado = 'NAO_SE_APLICA_MAIS', condicao_presente = false,"
                    + " estado_motivo = 'de-para da conta 8888 confirmado por admin em 04/10/2026' where id = '" + finding
                    + "'")).isEqualTo(1);
            assertThatThrownBy(() -> s.executeUpdate("update achado set estado = 'APAGADO'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("ck_achado_estado");
            assertThatThrownBy(() -> s.executeUpdate("delete from achado")).isInstanceOf(SQLException.class)
                    .hasMessageContaining("Nada é apagado");

            s.executeUpdate("insert into evento_achado (id, achado_id, condominio_id, estado_anterior, estado_novo,"
                    + " condicao_presente, motivo, usuario, em) values ('" + UUID.randomUUID() + "', '" + finding + "', '"
                    + CONDOMINIUM + "', 'ABERTO', 'NAO_SE_APLICA_MAIS', false, 'teste', 'admin', now())");
            assertThatThrownBy(() -> s.executeUpdate("update evento_achado set motivo = 'outro'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("só de inserção");
            assertThatThrownBy(() -> s.executeUpdate("delete from evento_achado"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("só de inserção");
        }
    }

    private record BaseRows(UUID file, UUID budget, UUID line) {
    }

    private static BaseRows baseRows(Statement s) throws SQLException {
        UUID file = UUID.randomUUID();
        UUID budget = UUID.randomUUID();
        UUID line = UUID.randomUUID();
        s.executeUpdate("insert into arquivo (id, condominio_id, categoria, nome_original, caminho, sha256, tamanho_bytes,"
                + " status, enviado_por, enviado_em, processamento_id) values ('" + file + "', '" + CONDOMINIUM
                + "', 'PO', 'po.pdf', 'c/po.pdf', '" + "a".repeat(64) + "', 1, 'CONCLUIDO', 'admin', now(), '"
                + UUID.randomUUID() + "')");
        s.executeUpdate("insert into previsao_orcamentaria (id, condominio_id, arquivo_id, sha256, estado,"
                + " tolerancia_arredondamento, lida_em) values ('" + budget + "', '" + CONDOMINIUM + "', '" + file
                + "', '" + "a".repeat(64) + "', 'CONFIRMADA', 0.01, now())");
        s.executeUpdate("insert into linha_po (id, previsao_id, condominio_id, arquivo_id, sha256, ordem, pagina, tipo,"
                + " codigo_impresso, codigo_efetivo, descricao, orcado_anterior, orcado) values ('" + line + "', '"
                + budget + "', '" + CONDOMINIUM + "', '" + file + "', '" + "a".repeat(64) + "', 1, 1, 'LINHA',"
                + " '1.7.9', '1.7.9', 'Material de pintura', 0, 2300.00)");
        return new BaseRows(file, budget, line);
    }

    private static UUID insertReallocation(Statement s, BaseRows b) throws SQLException {
        UUID id = UUID.randomUUID();
        s.executeUpdate("insert into realocacao (id, condominio_id, previsao_id, chave_lancamento, arquivo_id, sha256,"
                + " pagina, ordem, data, conta_codigo, historico, valor, linha_po_id, realocada_por, realocada_em)"
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
