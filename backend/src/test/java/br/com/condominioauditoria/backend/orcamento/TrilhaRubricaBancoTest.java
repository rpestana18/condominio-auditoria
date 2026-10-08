package br.com.condominioauditoria.backend.orcamento;

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
 * RF-11.7 e ADR 0005, Decisão 1: a trilha das rubricas é só de inserção, e quem recusa update e delete é o banco
 * (gatilho da V14), não o código. Uma rubrica por linha da PO. Roda as migrações reais num schema temporário do
 * PostgreSQL e apaga o schema no fim. Sem banco acessível, o teste é pulado (variáveis como no TrilhaDeparaBancoTest).
 */
class TrilhaRubricaBancoTest {

    private static final String CONDOMINIO = "6f1d2c1e-3b4a-4c8e-9a51-2815a0000001"; // piloto (V2)

    private final String url = env("BANCO_TESTE_URL", "jdbc:postgresql://localhost:5432/condominio");
    private final String usuario = env("BANCO_TESTE_USUARIO", "condominio");
    private final String senha = env("BANCO_TESTE_SENHA", "condominio");
    private final String schema = "teste_rubrica_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private Connection conexao;

    @BeforeEach
    void migrar() {
        try {
            conexao = DriverManager.getConnection(url, usuario, senha);
        } catch (SQLException e) {
            assumeTrue(false, "PostgreSQL de teste indisponível: " + e.getMessage());
        }
        Flyway.configure().dataSource(url, usuario, senha).schemas(schema).defaultSchema(schema)
                .createSchemas(true).load().migrate();
    }

    @AfterEach
    void apagar() throws SQLException {
        if (conexao != null) {
            try (Statement s = conexao.createStatement()) {
                s.execute("drop schema if exists " + schema + " cascade");
            }
            conexao.close();
        }
    }

    @Test
    void updateEDeleteNaTrilhaDasRubricasSaoRecusadosPeloBanco() throws SQLException {
        try (Statement s = conexao.createStatement()) {
            s.execute("set search_path to " + schema);
            Ids ids = inserir(s);

            assertThatThrownBy(() -> s.executeUpdate("update evento_rubrica set usuario = 'outro' where id = '"
                    + ids.evento() + "'")).isInstanceOf(SQLException.class).hasMessageContaining("só de inserção")
                    .hasMessageContaining("UPDATE");
            assertThatThrownBy(() -> s.executeUpdate("delete from evento_rubrica where id = '" + ids.evento() + "'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("só de inserção")
                    .hasMessageContaining("DELETE");
            try (var r = s.executeQuery("select usuario from evento_rubrica where id = '" + ids.evento() + "'")) {
                assertThat(r.next()).isTrue();
                assertThat(r.getString(1)).isEqualTo("admin");
            }
            // O estado atual aceita mudança; só a trilha é imutável
            assertThat(s.executeUpdate("update linha_rubrica set estado = 'CONFIRMADO'")).isEqualTo(1);
            assertThat(s.executeUpdate("update rubrica set nome = 'Sindicatura'")).isEqualTo(1);
        }
    }

    @Test
    void cadaLinhaTemUmaRubricaSoEEstadoValido() throws SQLException {
        try (Statement s = conexao.createStatement()) {
            s.execute("set search_path to " + schema);
            Ids ids = inserir(s);
            assertThatThrownBy(() -> s.executeUpdate(linhaRubricaSql(ids, "SUGERIDO")))
                    .isInstanceOf(SQLException.class).hasMessageContaining("uk_linha_rubrica");
            s.executeUpdate("delete from linha_rubrica");
            assertThatThrownBy(() -> s.executeUpdate(linhaRubricaSql(ids, "TALVEZ")))
                    .isInstanceOf(SQLException.class).hasMessageContaining("ck_linha_rubrica_estado");
        }
    }

    private record Ids(UUID previsao, UUID linha, UUID rubrica, UUID evento) {
    }

    private static Ids inserir(Statement s) throws SQLException {
        UUID arquivo = UUID.randomUUID();
        UUID previsao = UUID.randomUUID();
        UUID linha = UUID.randomUUID();
        UUID rubrica = UUID.randomUUID();
        UUID evento = UUID.randomUUID();
        s.executeUpdate("insert into arquivo (id, condominio_id, categoria, nome_original, caminho, sha256, tamanho_bytes,"
                + " status, enviado_por, enviado_em, processamento_id) values ('" + arquivo + "', '" + CONDOMINIO
                + "', 'PO', 'po.pdf', 'c/po.pdf', '" + "a".repeat(64) + "', 1, 'CONCLUIDO', 'admin', now(), '"
                + UUID.randomUUID() + "')");
        s.executeUpdate("insert into previsao_orcamentaria (id, condominio_id, arquivo_id, sha256, estado,"
                + " tolerancia_arredondamento, lida_em) values ('" + previsao + "', '" + CONDOMINIO + "', '" + arquivo
                + "', '" + "a".repeat(64) + "', 'CONFIRMADA', 0.01, now())");
        s.executeUpdate("insert into linha_po (id, previsao_id, condominio_id, arquivo_id, sha256, ordem, pagina, tipo,"
                + " codigo_impresso, codigo_efetivo, conta, descricao, orcado_anterior, orcado) values ('" + linha
                + "', '" + previsao + "', '" + CONDOMINIO + "', '" + arquivo + "', '" + "a".repeat(64)
                + "', 1, 1, 'LINHA', '1.3.20', '1.3.20', '1682 - Sindicatura Profissional', 'Obm', 17195.00, 8000.00)");
        s.executeUpdate("insert into rubrica (id, condominio_id, nome, grupo_codigo, linha_origem_id, criada_por,"
                + " criada_em) values ('" + rubrica + "', '" + CONDOMINIO + "', '1682 - Sindicatura Profissional', '1.3', '"
                + linha + "', 'admin', now())");
        Ids ids = new Ids(previsao, linha, rubrica, evento);
        s.executeUpdate(linhaRubricaSql(ids, "SUGERIDO"));
        s.executeUpdate("insert into evento_rubrica (id, condominio_id, previsao_id, linha_po_id, linha_codigo, acao,"
                + " usuario, em, rubrica_nova_id, rubrica_nova, estado_novo, origem) values ('" + evento + "', '"
                + CONDOMINIO + "', '" + previsao + "', '" + linha + "', '1.3.20', 'SUGERIDO', 'admin', now(), '" + rubrica
                + "', '1682 - Sindicatura Profissional', 'SUGERIDO', 'CONTA_PO')");
        return ids;
    }

    private static String linhaRubricaSql(Ids ids, String estado) {
        return "insert into linha_rubrica (id, condominio_id, previsao_id, linha_po_id, rubrica_id, estado, origem,"
                + " atualizado_por, atualizado_em) values ('" + UUID.randomUUID() + "', '" + CONDOMINIO + "', '"
                + ids.previsao() + "', '" + ids.linha() + "', '" + ids.rubrica() + "', '" + estado
                + "', 'CONTA_PO', 'admin', now())";
    }

    private static String env(String nome, String padrao) {
        String v = System.getenv(nome);
        return v == null || v.isBlank() ? padrao : v;
    }
}
