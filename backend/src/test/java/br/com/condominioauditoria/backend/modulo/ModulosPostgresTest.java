package br.com.condominioauditoria.backend.modulo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Contra um PostgreSQL real (só roda com a variável BANCO_TESTE, ex.: jdbc:postgresql://localhost:55432/condominio):
 * Flyway até a V10, validação do Hibernate, piloto ligado, trilha e uso só de inclusão (gatilhos), períodos e o resumo
 * de uso pela consulta nativa. Usa condomínios novos a cada execução, porque a trilha não pode ser apagada.
 *
 * Sem a variável, o teste é pulado (o build não depende de banco).
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "BANCO_TESTE", matches = "jdbc:postgresql:.+")
class ModulosPostgresTest {

    private static final UUID PILOTO = UUID.fromString("6f1d2c1e-3b4a-4c8e-9a51-2815a0000001");

    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry registro) throws Exception {
        registro.add("spring.datasource.url", () -> System.getenv("BANCO_TESTE") + "?currentSchema=backend");
        registro.add("spring.rabbitmq.listener.simple.auto-startup", () -> "false");
        registro.add("condominio.grpc.porta", () -> "0");
        String pasta = Files.createTempDirectory("dados-teste").toString();
        registro.add("condominio.armazenamento.pasta", () -> pasta);
    }

    @Autowired
    Modulos modulos;
    @Autowired
    RegistroUso registroUso;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void pilotoNasceLigadoComOEventoDaImplantacao() {
        assertThat(modulos.ligado(PILOTO, Modulos.ASSISTENTE)).isTrue();
        assertThat(modulos.eventos(PILOTO, Modulos.ASSISTENTE)).first().satisfies(e -> {
            assertThat(e.isLigadoAntes()).isFalse();
            assertThat(e.isLigadoDepois()).isTrue();
            assertThat(e.getMotivo()).isEqualTo("Implantação do piloto");
        });
        assertThat(modulos.periodos(PILOTO, Modulos.ASSISTENTE)).first()
                .satisfies(p -> assertThat(p.fim()).isNull());
    }

    @Test
    void condominioNovoComecaDesligadoELigarEDesligarFormaUmPeriodo() {
        UUID novo = novoCondominio();
        assertThat(modulos.ligado(novo, Modulos.ASSISTENTE)).isFalse();

        modulos.alterar(novo, Modulos.ASSISTENTE, true, "Contrato assinado", "admin");
        assertThat(modulos.ligado(novo, Modulos.ASSISTENTE)).isTrue();
        modulos.alterar(novo, Modulos.ASSISTENTE, true, "repetido", "admin"); // sem mudança, sem evento
        modulos.alterar(novo, Modulos.ASSISTENTE, false, "Fim do teste", "outro.admin");

        assertThat(modulos.eventos(novo, Modulos.ASSISTENTE)).hasSize(2);
        assertThat(modulos.periodos(novo, Modulos.ASSISTENTE)).singleElement().satisfies(p -> {
            assertThat(p.ligadoPor()).isEqualTo("admin");
            assertThat(p.desligadoPor()).isEqualTo("outro.admin");
            assertThat(p.fim()).isAfterOrEqualTo(p.inicio());
        });
        assertThat(modulos.ligados(novo)).isEmpty();
    }

    @Test
    void trilhaEUsoRecusamUpdateDeleteETruncate() {
        UUID novo = novoCondominio();
        modulos.alterar(novo, Modulos.ASSISTENTE, true, "Contrato assinado", "admin");
        registroUso.chamadaMcp(novo, "conselheiro", true);

        assertThatThrownBy(() -> jdbc.update("update evento_modulo set motivo = 'outro' where condominio_id = ?", novo))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("só aceita inclusão");
        assertThatThrownBy(() -> jdbc.update("delete from evento_modulo where condominio_id = ?", novo))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("só aceita inclusão");
        assertThatThrownBy(() -> jdbc.execute("truncate evento_modulo"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("só aceita inclusão");
        assertThatThrownBy(() -> jdbc.update("update uso_modulo set usuario = 'x' where condominio_id = ?", novo))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("só aceita inclusão");
        assertThatThrownBy(() -> jdbc.update("delete from uso_modulo where condominio_id = ?", novo))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("só aceita inclusão");
        assertThat(jdbc.queryForObject("select count(*) from evento_modulo where condominio_id = ?", Long.class, novo))
                .isEqualTo(1);
    }

    @Test
    void bancoAceitaMotivoNuloERecusaMotivoEmBrancoEFuncaoDesconhecida() {
        UUID novo = novoCondominio();
        assertThatThrownBy(() -> jdbc.update("insert into evento_modulo values (gen_random_uuid(), ?, 'ASSISTENTE',"
                + " false, true, 'admin', now(), '   ')", novo)).isInstanceOf(DataAccessException.class);
        modulos.alterar(novo, Modulos.ASSISTENTE, true, null, "admin");
        assertThat(modulos.eventos(novo, Modulos.ASSISTENTE)).singleElement()
                .satisfies(e -> assertThat(e.getMotivo()).isNull());
        assertThatThrownBy(() -> jdbc.update("insert into uso_modulo (id, condominio_id, modulo, funcao, quando)"
                + " values (gen_random_uuid(), ?, 'ASSISTENTE', 'chat', now())", novo))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void resumoDeUsoPorFuncaoEPorMes() {
        UUID novo = novoCondominio();
        registroUso.indexacao(novo, 7, "bge-m3");
        registroUso.indexacao(novo, 3, null);
        registroUso.chamadaMcp(novo, "conselheiro", true);
        registroUso.chamadaMcp(novo, "conselheiro", false);
        LocalDate hoje = LocalDate.now(RegistroUso.FUSO);

        var resumo = registroUso.resumo(novo, hoje.withDayOfMonth(1), hoje);

        assertThat(resumo.porFuncao()).containsExactly(
                new TotalUso(null, Modulos.ASSISTENTE, FuncaoUso.CHAMADA_MCP, 2, 0, 0, 0, 0),
                new TotalUso(null, Modulos.ASSISTENTE, FuncaoUso.INDEXACAO, 2, 0, 0, 2, 10));
        assertThat(resumo.porMes()).extracting(TotalUso::mes).containsOnly(hoje.toString().substring(0, 7));
        assertThat(registroUso.resumo(novo, hoje.minusYears(1), hoje.minusYears(1)).porMes()).isEmpty();
    }

    /** Primeira ligação de dois ADMINs ao mesmo tempo: sem erro, uma linha e um evento só. */
    @Test
    void primeiraLigacaoConcorrenteNaoDaErroNemDuplicaEvento() throws Exception {
        UUID novo = novoCondominio();
        int pedidos = 8;
        var largada = new CountDownLatch(1);
        try (ExecutorService threads = Executors.newFixedThreadPool(pedidos)) {
            List<Future<?>> resultados = new ArrayList<>();
            for (int i = 0; i < pedidos; i++) {
                String usuario = "admin" + i;
                resultados.add(threads.submit(() -> {
                    largada.await();
                    return modulos.alterar(novo, Modulos.ASSISTENTE, true, "ao mesmo tempo", usuario);
                }));
            }
            largada.countDown();
            for (Future<?> r : resultados) {
                r.get(30, TimeUnit.SECONDS); // lança se algum pedido falhou
            }
        }
        assertThat(modulos.ligado(novo, Modulos.ASSISTENTE)).isTrue();
        assertThat(modulos.eventos(novo, Modulos.ASSISTENTE)).hasSize(1);
    }

    private UUID novoCondominio() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into condominio (id, nome) values (?, ?)", id, "Teste " + id);
        return id;
    }
}
