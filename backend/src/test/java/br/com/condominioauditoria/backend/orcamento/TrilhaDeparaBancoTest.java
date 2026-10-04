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
 * RF-03.1.4 e ADR 0004, Decisão 3: a trilha do de-para é só de inserção, e quem recusa update e delete é o banco
 * (gatilho da V8), não o código. Roda as migrações reais num schema temporário do PostgreSQL e apaga o schema no fim.
 *
 * <p>Banco: variáveis BANCO_TESTE_URL, BANCO_TESTE_USUARIO e BANCO_TESTE_SENHA, ou o padrão do application.yml
 * (localhost:5432/condominio, usuário condominio). Sem banco acessível, o teste é pulado.
 */
class TrilhaDeparaBancoTest {

    private final String url = env("BANCO_TESTE_URL", "jdbc:postgresql://localhost:5432/condominio");
    private final String usuario = env("BANCO_TESTE_USUARIO", "condominio");
    private final String senha = env("BANCO_TESTE_SENHA", "condominio");
    private final String schema = "teste_trilha_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
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
    void updateEDeleteNaTrilhaDoDeparaSaoRecusadosPeloBanco() throws SQLException {
        try (Statement s = conexao.createStatement()) {
            s.execute("set search_path to " + schema);
            UUID evento = inserirEvento(s);

            assertThatThrownBy(() -> s.executeUpdate("update evento_depara set usuario = 'outro' where id = '" + evento
                    + "'")).isInstanceOf(SQLException.class).hasMessageContaining("só de inserção")
                    .hasMessageContaining("UPDATE");
            assertThatThrownBy(() -> s.executeUpdate("delete from evento_depara where id = '" + evento + "'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("só de inserção")
                    .hasMessageContaining("DELETE");

            try (var r = s.executeQuery("select usuario from evento_depara where id = '" + evento + "'")) {
                assertThat(r.next()).isTrue();
                assertThat(r.getString(1)).isEqualTo("admin");
            }
            // A tabela do de-para (estado atual) aceita mudança; só a trilha é imutável
            assertThat(s.executeUpdate("update depara_conta set estado = 'CONFIRMADO'")).isEqualTo(1);
        }
    }

    @Test
    void deparaTemUmDestinoPorContaEDestinoCoerente() throws SQLException {
        try (Statement s = conexao.createStatement()) {
            s.execute("set search_path to " + schema);
            inserirEvento(s);
            String previsao = previsaoId(s);
            assertThatThrownBy(() -> s.executeUpdate(deparaSql(previsao, "1621", "AJUSTE", "null")))
                    .isInstanceOf(SQLException.class).hasMessageContaining("uk_depara_conta");
            assertThatThrownBy(() -> s.executeUpdate(deparaSql(previsao, "1622", "LINHA_PO", "null")))
                    .isInstanceOf(SQLException.class).hasMessageContaining("ck_depara_destino");
        }
    }

    private static UUID inserirEvento(Statement s) throws SQLException {
        String condominio = "6f1d2c1e-3b4a-4c8e-9a51-2815a0000001"; // piloto (V2)
        UUID arquivo = UUID.randomUUID();
        UUID previsao = UUID.randomUUID();
        UUID evento = UUID.randomUUID();
        s.executeUpdate("insert into arquivo (id, condominio_id, categoria, nome_original, caminho, sha256, tamanho_bytes,"
                + " status, enviado_por, enviado_em, processamento_id) values ('" + arquivo + "', '" + condominio
                + "', 'PO', 'po.pdf', 'c/po.pdf', '" + "a".repeat(64) + "', 1, 'CONCLUIDO', 'admin', now(), '"
                + UUID.randomUUID() + "')");
        s.executeUpdate("insert into previsao_orcamentaria (id, condominio_id, arquivo_id, sha256, estado,"
                + " tolerancia_arredondamento, lida_em) values ('" + previsao + "', '" + condominio + "', '" + arquivo
                + "', '" + "a".repeat(64) + "', 'CONFIRMADA', 0.01, now())");
        s.executeUpdate(deparaSql(previsao.toString(), "1621", "AJUSTE", "null"));
        s.executeUpdate("insert into evento_depara (id, condominio_id, previsao_id, conta_codigo, acao, usuario, em,"
                + " tipo_destino_novo, destino_novo, estado_novo, origem) values ('" + evento + "', '" + condominio
                + "', '" + previsao + "', '1621', 'SUGERIDO', 'admin', now(), 'AJUSTE', 'AJUSTE', 'SUGERIDO', 'ADMIN')");
        return evento;
    }

    private static String previsaoId(Statement s) throws SQLException {
        try (var r = s.executeQuery("select id from previsao_orcamentaria")) {
            r.next();
            return r.getString(1);
        }
    }

    private static String deparaSql(String previsao, String conta, String tipo, String linha) {
        return "insert into depara_conta (id, condominio_id, previsao_id, conta_codigo, tipo_destino, linha_po_id, estado,"
                + " origem, atualizado_por, atualizado_em) values ('" + UUID.randomUUID()
                + "', '6f1d2c1e-3b4a-4c8e-9a51-2815a0000001', '" + previsao + "', '" + conta + "', '" + tipo + "', "
                + linha + ", 'SUGERIDO', 'ADMIN', 'admin', now())";
    }

    private static String env(String nome, String padrao) {
        String v = System.getenv(nome);
        return v == null || v.isBlank() ? padrao : v;
    }
}
