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
 * V12 no PostgreSQL real (schema temporário): uma realocação ativa por lançamento e versão da PO; realocação e achado
 * nunca são apagados; as trilhas da realocação e do achado são só de inserção; o estado do achado é um dos previstos.
 * Banco como no {@link TrilhaDeparaBancoTest}; sem banco acessível, o teste é pulado.
 */
class RealocacaoAchadoBancoTest {

    private static final String CONDOMINIO = "6f1d2c1e-3b4a-4c8e-9a51-2815a0000001"; // piloto (V2)

    private final String url = env("BANCO_TESTE_URL", "jdbc:postgresql://localhost:5432/condominio");
    private final String usuario = env("BANCO_TESTE_USUARIO", "condominio");
    private final String senha = env("BANCO_TESTE_SENHA", "condominio");
    private final String schema = "teste_realoc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
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
    void umaRealocacaoAtivaPorLancamentoENadaEhApagado() throws SQLException {
        try (Statement s = conexao.createStatement()) {
            s.execute("set search_path to " + schema);
            Base b = base(s);
            UUID primeira = inserirRealocacao(s, b);

            assertThatThrownBy(() -> inserirRealocacao(s, b)).isInstanceOf(SQLException.class)
                    .hasMessageContaining("uk_realocacao_ativa");
            // Desfeita, a mesma chave pode ser realocada de novo
            s.executeUpdate("update realocacao set desfeita_por = 'admin', desfeita_em = now() where id = '"
                    + primeira + "'");
            inserirRealocacao(s, b);
            assertThatThrownBy(() -> s.executeUpdate("delete from realocacao where id = '" + primeira + "'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("Nada é apagado");
            assertThatThrownBy(() -> s.executeUpdate("update realocacao set desfeita_em = null where id = '"
                    + primeira + "'")).isInstanceOf(SQLException.class).hasMessageContaining("ck_realocacao_desfeita");

            UUID evento = UUID.randomUUID();
            s.executeUpdate("insert into evento_realocacao (id, realocacao_id, condominio_id, acao, usuario, em, detalhe)"
                    + " values ('" + evento + "', '" + primeira + "', '" + CONDOMINIO + "', 'REALOCADA', 'gestor', now(),"
                    + " 'teste')");
            assertThatThrownBy(() -> s.executeUpdate("update evento_realocacao set usuario = 'outro'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("só de inserção");
            assertThatThrownBy(() -> s.executeUpdate("delete from evento_realocacao"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("só de inserção");
        }
    }

    @Test
    void achadoNaoEhApagadoTemEstadoValidoEHistoricoSoDeInsercao() throws SQLException {
        try (Statement s = conexao.createStatement()) {
            s.execute("set search_path to " + schema);
            UUID achado = UUID.randomUUID();
            s.executeUpdate("insert into achado (id, condominio_id, regra, versao_regra, severidade, competencia, alvo,"
                    + " descricao, estado, criado_em) values ('" + achado + "', '" + CONDOMINIO + "',"
                    + " 'CONTA_SEM_LINHA_PO', '1', 'ATENCAO', date '2026-09-01', 'conta:8888', 'teste', 'ABERTO', now())");

            assertThat(s.executeUpdate("update achado set estado = 'NAO_SE_APLICA_MAIS', condicao_presente = false,"
                    + " estado_motivo = 'de-para da conta 8888 confirmado por admin em 04/10/2026' where id = '" + achado
                    + "'")).isEqualTo(1);
            assertThatThrownBy(() -> s.executeUpdate("update achado set estado = 'APAGADO'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("ck_achado_estado");
            assertThatThrownBy(() -> s.executeUpdate("delete from achado")).isInstanceOf(SQLException.class)
                    .hasMessageContaining("Nada é apagado");

            s.executeUpdate("insert into evento_achado (id, achado_id, condominio_id, estado_anterior, estado_novo,"
                    + " condicao_presente, motivo, usuario, em) values ('" + UUID.randomUUID() + "', '" + achado + "', '"
                    + CONDOMINIO + "', 'ABERTO', 'NAO_SE_APLICA_MAIS', false, 'teste', 'admin', now())");
            assertThatThrownBy(() -> s.executeUpdate("update evento_achado set motivo = 'outro'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("só de inserção");
            assertThatThrownBy(() -> s.executeUpdate("delete from evento_achado"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("só de inserção");
        }
    }

    private record Base(UUID arquivo, UUID previsao, UUID linha) {
    }

    private static Base base(Statement s) throws SQLException {
        UUID arquivo = UUID.randomUUID();
        UUID previsao = UUID.randomUUID();
        UUID linha = UUID.randomUUID();
        s.executeUpdate("insert into arquivo (id, condominio_id, categoria, nome_original, caminho, sha256, tamanho_bytes,"
                + " status, enviado_por, enviado_em, processamento_id) values ('" + arquivo + "', '" + CONDOMINIO
                + "', 'PO', 'po.pdf', 'c/po.pdf', '" + "a".repeat(64) + "', 1, 'CONCLUIDO', 'admin', now(), '"
                + UUID.randomUUID() + "')");
        s.executeUpdate("insert into previsao_orcamentaria (id, condominio_id, arquivo_id, sha256, estado,"
                + " tolerancia_arredondamento, lida_em) values ('" + previsao + "', '" + CONDOMINIO + "', '" + arquivo
                + "', '" + "a".repeat(64) + "', 'CONFIRMADA', 0.01, now())");
        s.executeUpdate("insert into linha_po (id, previsao_id, condominio_id, arquivo_id, sha256, ordem, pagina, tipo,"
                + " codigo_impresso, codigo_efetivo, descricao, orcado_anterior, orcado) values ('" + linha + "', '"
                + previsao + "', '" + CONDOMINIO + "', '" + arquivo + "', '" + "a".repeat(64) + "', 1, 1, 'LINHA',"
                + " '1.7.9', '1.7.9', 'Material de pintura', 0, 2300.00)");
        return new Base(arquivo, previsao, linha);
    }

    private static UUID inserirRealocacao(Statement s, Base b) throws SQLException {
        UUID id = UUID.randomUUID();
        s.executeUpdate("insert into realocacao (id, condominio_id, previsao_id, chave_lancamento, arquivo_id, sha256,"
                + " pagina, ordem, data, conta_codigo, historico, valor, linha_po_id, realocada_por, realocada_em)"
                + " values ('" + id + "', '" + CONDOMINIO + "', '" + b.previsao() + "', '" + "c".repeat(64) + "', '"
                + b.arquivo() + "', '" + "a".repeat(64) + "', 3, 12, date '2026-09-09', '1064', 'Compra no cartão',"
                + " 250.00, '" + b.linha() + "', 'gestor', now())");
        return id;
    }

    private static String env(String nome, String padrao) {
        String v = System.getenv(nome);
        return v == null || v.isBlank() ? padrao : v;
    }
}
