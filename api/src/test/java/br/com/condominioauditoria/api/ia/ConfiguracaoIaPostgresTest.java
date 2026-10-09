package br.com.condominioauditoria.api.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.api.grpc.ClienteAssistente;
import br.com.condominioauditoria.api.ia.ConfiguracaoIaServico.Pedido;
import br.com.condominioauditoria.api.ia.ConfiguracaoIaServico.PedidoEmbeddings;
import br.com.condominioauditoria.api.ia.ConfiguracaoIaServico.PedidoRespostas;
import br.com.condominioauditoria.api.modulo.ModoIa;
import br.com.condominioauditoria.api.modulo.Modulos;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Configuração de IA contra um PostgreSQL real (só roda com BANCO_TESTE, ex.: jdbc:postgresql://localhost:55433/condominio):
 * Flyway até a V13, validação do Hibernate, gravação com a chave cifrada em bytea, trilha só de inserção (gatilho
 * recusar_alteracao_trilha da V7), unicidade por condomínio + módulo + função (com módulo nulo) e restrições do modo
 * geral. O rag é falso (catálogo e chave pública gerados no teste).
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "BANCO_TESTE", matches = "jdbc:postgresql:.+")
class ConfiguracaoIaPostgresTest {

    private static final String CHAVE = "sk-ant-api03-ChaveDeTesteNaoReal-x9Qa";

    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry registro) throws Exception {
        registro.add("spring.datasource.url", () -> System.getenv("BANCO_TESTE") + "?currentSchema=backend");
        registro.add("spring.rabbitmq.listener.simple.auto-startup", () -> "false");
        registro.add("condominio.grpc.port", () -> "0");
        String pasta = Files.createTempDirectory("dados-teste").toString();
        registro.add("condominio.storage.folder", () -> pasta);
    }

    @MockitoBean
    ClienteAssistente rag;
    @Autowired
    ConfiguracaoIaServico servico;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void catalogo() {
        when(rag.listarProvedores(anyString()))
                .thenReturn(CatalogoTeste.resposta(ParDeChavesTeste.pemPublica(ParDeChavesTeste.par())));
    }

    @Test
    void semLinhaValemOsPadroesEGravarCifraEGuardaATrilhaSemAChave() throws Exception {
        UUID novo = novoCondominio();
        assertThat(servico.ler(novo).modoGeral()).isEqualTo(ModoIa.MCP_EXTERNO);
        assertThat(jdbc.queryForObject("select count(*) from configuracao_ia where condominio_id = ?", Long.class,
                novo)).isZero();

        servico.gravar(novo, new Pedido(ModoIa.DESLIGADO, new PedidoRespostas(ModoIa.API_KEY, "anthropic", null, CHAVE,
                false), new PedidoEmbeddings(ModoIa.DESLIGADO, null, null)), "admin", "Bearer t");

        var e = servico.ler(novo);
        assertThat(e.modoGeral()).isEqualTo(ModoIa.DESLIGADO);
        assertThat(e.respostas().modoEfetivo()).isEqualTo(ModoIa.API_KEY);
        assertThat(e.respostas().chaveFinal()).isEqualTo("x9Qa");
        assertThat(ParDeChavesTeste.decifrar(e.respostas().chaveCifrada(), ParDeChavesTeste.par().getPrivate()))
                .isEqualTo(CHAVE);
        assertThat(e.embeddings().modo()).isEqualTo(ModoIa.DESLIGADO);

        List<Map<String, Object>> trilha = jdbc.queryForList(
                "select * from evento_configuracao_ia where condominio_id = ? order by funcao, modulo nulls first", novo);
        assertThat(trilha).hasSize(3);
        assertThat(trilha).allSatisfy(linha -> assertThat(linha.values()).noneMatch(
                v -> v != null && v.toString().contains("sk-ant")));
        assertThat(trilha).anySatisfy(linha -> {
            assertThat(linha.get("funcao")).isEqualTo("RESPOSTAS");
            assertThat(linha.get("modulo")).isEqualTo(Modulos.ASSISTENTE);
            assertThat(linha.get("chave_trocada")).isEqualTo(true);
            assertThat(linha.get("chave_final")).isEqualTo("x9Qa");
            assertThat(linha.get("modo_novo")).isEqualTo("API_KEY");
        });

        // Mesmo pedido de novo (sem reenviar a chave): nada muda, nenhum evento
        servico.gravar(novo, new Pedido(ModoIa.DESLIGADO, new PedidoRespostas(ModoIa.API_KEY, "anthropic", null, null,
                false), new PedidoEmbeddings(ModoIa.DESLIGADO, null, null)), "admin", "Bearer t");
        assertThat(jdbc.queryForObject("select count(*) from evento_configuracao_ia where condominio_id = ?",
                Long.class, novo)).isEqualTo(3);
    }

    @Test
    void trilhaRecusaUpdateDeleteETruncate() {
        UUID novo = novoCondominio();
        servico.gravar(novo, new Pedido(ModoIa.DESLIGADO, new PedidoRespostas(null, null, null, null, false),
                new PedidoEmbeddings(ModoIa.LOCAL, "ollama-local", null)), "admin", "Bearer t");

        assertThatThrownBy(() -> jdbc.update("update evento_configuracao_ia set usuario = 'x' where condominio_id = ?",
                novo)).isInstanceOf(DataAccessException.class).hasMessageContaining("só de inserção");
        assertThatThrownBy(() -> jdbc.update("delete from evento_configuracao_ia where condominio_id = ?", novo))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("só de inserção");
        assertThatThrownBy(() -> jdbc.execute("truncate evento_configuracao_ia"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("só de inserção");
    }

    @Test
    void umaLinhaPorCondominioModuloEFuncaoMesmoComModuloNulo() {
        UUID novo = novoCondominio();
        String geral = "insert into configuracao_ia (id, condominio_id, modulo, funcao, modo, atualizado_por,"
                + " atualizado_em) values (gen_random_uuid(), ?, null, 'RESPOSTAS', 'DESLIGADO', 'admin', now())";
        jdbc.update(geral, novo);
        assertThatThrownBy(() -> jdbc.update(geral, novo)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void modoGeralNaoTemProvedorNemChaveEChaveSemFinalEhRecusada() {
        UUID novo = novoCondominio();
        assertThatThrownBy(() -> jdbc.update("insert into configuracao_ia (id, condominio_id, modulo, funcao, modo,"
                + " provedor, atualizado_por, atualizado_em) values (gen_random_uuid(), ?, null, 'RESPOSTAS',"
                + " 'API_KEY', 'anthropic', 'admin', now())", novo)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("insert into configuracao_ia (id, condominio_id, modulo, funcao, modo,"
                + " chave_cifrada, atualizado_por, atualizado_em) values (gen_random_uuid(), ?, 'ASSISTENTE',"
                + " 'RESPOSTAS', 'API_KEY', '\\x01'::bytea, 'admin', now())", novo))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("insert into configuracao_ia (id, condominio_id, modulo, funcao, modo,"
                + " atualizado_por, atualizado_em) values (gen_random_uuid(), ?, 'ASSISTENTE', 'EMBEDDINGS', null,"
                + " 'admin', now())", novo)).isInstanceOf(DataAccessException.class);
    }

    private UUID novoCondominio() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into condominio (id, nome) values (?, ?)", id, "Teste IA " + id);
        return id;
    }
}
