package br.com.condominioauditoria.api.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.api.grpc.ClienteAssistente;
import br.com.condominioauditoria.api.ia.ConfiguracaoIaServico.Pedido;
import br.com.condominioauditoria.api.ia.ConfiguracaoIaServico.PedidoEmbeddings;
import br.com.condominioauditoria.api.ia.ConfiguracaoIaServico.PedidoRespostas;
import br.com.condominioauditoria.api.modulo.ModoIa;
import br.com.condominioauditoria.api.modulo.Modulos;
import br.com.condominioauditoria.api.modulo.PedidoInvalidoException;
import io.grpc.Status;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Configuração de IA (RF-09.1, 09.2, 09.6): padrões sem linha, herança do modo geral, recusas 422 do contrato, chave
 * cifrada que só o rag decifra, trilha com anterior e novo e nunca a chave.
 */
class ConfiguracaoIaServicoTest {

    private static final UUID CONDOMINIO = UUID.randomUUID();
    private static final String CHAVE = "sk-ant-api03-ChaveDeTesteNaoReal-x9Qa";
    private static final PedidoEmbeddings EMB_PADRAO = new PedidoEmbeddings(ModoIa.LOCAL, "ollama-local", null);

    private final ConfiguracaoIaRepository configuracoes = mock(ConfiguracaoIaRepository.class);
    private final EventoConfiguracaoIaRepository eventos = mock(EventoConfiguracaoIaRepository.class);
    private final ClienteAssistente rag = mock(ClienteAssistente.class);
    private final Modulos modulos = mock(Modulos.class);
    private final List<ConfiguracaoIa> linhas = new ArrayList<>();
    private final List<EventoConfiguracaoIa> trilha = new ArrayList<>();
    private ConfiguracaoIaServico servico;

    @BeforeEach
    void preparar() {
        when(configuracoes.findByCondominioId(any())).thenAnswer(i -> linhas.stream()
                .filter(l -> l.getCondominioId().equals(i.getArgument(0))).toList());
        when(configuracoes.save(any())).thenAnswer(i -> {
            ConfiguracaoIa c = i.getArgument(0);
            linhas.removeIf(l -> l.getId().equals(c.getId()));
            linhas.add(c);
            return c;
        });
        when(eventos.save(any())).thenAnswer(i -> {
            trilha.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(rag.listarProvedores(anyString()))
                .thenReturn(CatalogoTeste.resposta(ParDeChavesTeste.pemPublica(ParDeChavesTeste.par())));
        servico = new ConfiguracaoIaServico(configuracoes, eventos, new CatalogoIa(rag, Duration.ZERO,
                java.time.Clock.systemUTC()), modulos, TransactionOperations.withoutTransaction());
    }

    @Test
    void semLinhaValemOsPadroes() {
        var e = servico.ler(CONDOMINIO);

        assertThat(e.modoGeral()).isEqualTo(ModoIa.MCP_EXTERNO);
        assertThat(e.respostas().modo()).isNull();
        assertThat(e.respostas().modoEfetivo()).isEqualTo(ModoIa.MCP_EXTERNO);
        assertThat(e.respostas().chaveCadastrada()).isFalse();
        assertThat(e.embeddings()).isEqualTo(new ConfiguracaoIaServico.Embeddings(ModoIa.LOCAL, "ollama-local",
                "bge-m3"));
        assertThat(e.atualizadoPor()).isNull();
        assertThat(e.atualizadoEm()).isNull();
    }

    @Test
    void pedirOsPadroesNaoGravaNadaNemChamaORag() {
        servico.gravar(CONDOMINIO, new Pedido(ModoIa.MCP_EXTERNO, new PedidoRespostas(null, null, null, null, false),
                new PedidoEmbeddings(ModoIa.DESLIGADO, null, null)), "admin", "Bearer t");
        trilha.clear();
        linhas.clear();

        servico.gravar(CONDOMINIO, new Pedido(ModoIa.MCP_EXTERNO, new PedidoRespostas(null, null, null, null, false),
                EMB_PADRAO), "admin", "Bearer t");

        assertThat(linhas).isEmpty();
        assertThat(trilha).isEmpty();
    }

    @Test
    void desligadoSemProvedorNaoPrecisaDoCatalogo() {
        servico.gravar(CONDOMINIO, new Pedido(ModoIa.DESLIGADO, new PedidoRespostas(null, null, null, null, false),
                new PedidoEmbeddings(ModoIa.DESLIGADO, "ignorado", "ignorado")), "admin", "Bearer t");

        verify(rag, never()).listarProvedores(anyString());
        var e = servico.ler(CONDOMINIO);
        assertThat(e.modoGeral()).isEqualTo(ModoIa.DESLIGADO);
        assertThat(e.respostas().modoEfetivo()).isEqualTo(ModoIa.DESLIGADO);
        assertThat(e.embeddings()).isEqualTo(new ConfiguracaoIaServico.Embeddings(ModoIa.DESLIGADO, null, null));
        assertThat(e.atualizadoPor()).isEqualTo("admin");
    }

    @Test
    void apiKeyComChaveCifraComAChavePublicaDoRagEGuardaOsQuatroUltimos() throws Exception {
        var e = servico.gravar(CONDOMINIO, new Pedido(ModoIa.MCP_EXTERNO,
                new PedidoRespostas(ModoIa.API_KEY, "anthropic", null, "  " + CHAVE + " ", false), EMB_PADRAO),
                "admin", "Bearer t");

        assertThat(e.respostas().modoEfetivo()).isEqualTo(ModoIa.API_KEY);
        assertThat(e.respostas().provedor()).isEqualTo("anthropic");
        assertThat(e.respostas().modelo()).isEqualTo("claude-sonnet-5-5"); // padrão do provedor
        assertThat(e.respostas().chaveCadastrada()).isTrue();
        assertThat(e.respostas().chaveFinal()).isEqualTo("x9Qa");
        assertThat(e.respostas().chatDisponivel()).isTrue();
        assertThat(ParDeChavesTeste.decifrar(e.respostas().chaveCifrada(), ParDeChavesTeste.par().getPrivate()))
                .isEqualTo(CHAVE);
        assertThat(e.toString()).doesNotContain(CHAVE);

        assertThat(trilha).singleElement().satisfies(ev -> {
            assertThat(ev.getModulo()).isEqualTo(Modulos.ASSISTENTE);
            assertThat(ev.getFuncao()).isEqualTo(FuncaoIa.RESPOSTAS);
            assertThat(ev.getUsuario()).isEqualTo("admin");
            assertThat(ev.getModoAnterior()).isNull();
            assertThat(ev.getModoNovo()).isEqualTo(ModoIa.API_KEY);
            assertThat(ev.getProvedorNovo()).isEqualTo("anthropic");
            assertThat(ev.getModeloNovo()).isEqualTo("claude-sonnet-5-5");
            assertThat(ev.isChaveTrocada()).isTrue();
            assertThat(ev.getChaveFinal()).isEqualTo("x9Qa");
        });
        assertThat(textosDaTrilha()).noneMatch(t -> t.contains(CHAVE.substring(0, 10)));
    }

    @Test
    void assistenteSemModoProprioHerdaOGeralEMudaJuntoComEle() {
        servico.gravar(CONDOMINIO, new Pedido(ModoIa.API_KEY,
                new PedidoRespostas(null, "anthropic", "claude-haiku-4-5", CHAVE, false), EMB_PADRAO), "admin",
                "Bearer t");
        assertThat(servico.ler(CONDOMINIO).respostas().modoEfetivo()).isEqualTo(ModoIa.API_KEY);

        servico.gravar(CONDOMINIO, new Pedido(ModoIa.MCP_EXTERNO,
                new PedidoRespostas(null, "anthropic", "claude-haiku-4-5", null, false), EMB_PADRAO), "admin",
                "Bearer t");

        var e = servico.ler(CONDOMINIO);
        assertThat(e.respostas().modo()).isNull();
        assertThat(e.respostas().modoEfetivo()).isEqualTo(ModoIa.MCP_EXTERNO);
        assertThat(e.respostas().chaveCadastrada()).isTrue(); // a chave fica guardada
        assertThat(e.respostas().chatDisponivel()).isFalse();
        assertThat(trilha).last().satisfies(ev -> {
            assertThat(ev.getModulo()).isNull(); // evento do modo geral
            assertThat(ev.getModoAnterior()).isEqualTo(ModoIa.API_KEY);
            assertThat(ev.getModoNovo()).isEqualTo(ModoIa.MCP_EXTERNO);
            assertThat(ev.isChaveTrocada()).isFalse();
        });
    }

    @Test
    void manterAChaveSemReenviarERemoverComRemoverChave() {
        servico.gravar(CONDOMINIO, new Pedido(ModoIa.MCP_EXTERNO,
                new PedidoRespostas(ModoIa.API_KEY, "anthropic", null, CHAVE, false), EMB_PADRAO), "admin", "Bearer t");
        byte[] guardada = servico.ler(CONDOMINIO).respostas().chaveCifrada();

        servico.gravar(CONDOMINIO, new Pedido(ModoIa.MCP_EXTERNO,
                new PedidoRespostas(ModoIa.API_KEY, "anthropic", "claude-haiku-4-5", null, false), EMB_PADRAO), "outro",
                "Bearer t");
        assertThat(servico.ler(CONDOMINIO).respostas().chaveCifrada()).isEqualTo(guardada);
        assertThat(trilha).last().satisfies(ev -> {
            assertThat(ev.getModeloAnterior()).isEqualTo("claude-sonnet-5-5");
            assertThat(ev.getModeloNovo()).isEqualTo("claude-haiku-4-5");
            assertThat(ev.isChaveTrocada()).isFalse();
            assertThat(ev.getChaveFinal()).isNull();
        });

        servico.gravar(CONDOMINIO, new Pedido(ModoIa.MCP_EXTERNO,
                new PedidoRespostas(ModoIa.DESLIGADO, "anthropic", "claude-haiku-4-5", null, true), EMB_PADRAO), "admin",
                "Bearer t");
        var e = servico.ler(CONDOMINIO);
        assertThat(e.respostas().chaveCadastrada()).isFalse();
        assertThat(e.respostas().chaveFinal()).isNull();
        assertThat(trilha).last().satisfies(ev -> {
            assertThat(ev.isChaveTrocada()).isTrue();
            assertThat(ev.getChaveFinal()).isNull(); // removida
        });
    }

    @Test
    void recusasDoContratoVemTodasDeUmaVez() {
        assertThatThrownBy(() -> servico.gravar(CONDOMINIO, new Pedido(ModoIa.MCP_EXTERNO,
                new PedidoRespostas(ModoIa.LOCAL, null, null, CHAVE, true),
                new PedidoEmbeddings(ModoIa.API_KEY, "voyage", null)), "admin", "Bearer t"))
                .isInstanceOfSatisfying(ConfiguracaoIaRecusadaException.class, e -> assertThat(e.motivos())
                        .anyMatch(m -> m.contains("LOCAL para as respostas"))
                        .anyMatch(m -> m.contains("não os dois"))
                        .anyMatch(m -> m.contains("só embeddings locais")));
        assertThat(linhas).isEmpty();
        assertThat(trilha).isEmpty();
    }

    @Test
    void apiKeySemChaveEhRecusado() {
        assertThatThrownBy(() -> servico.gravar(CONDOMINIO, new Pedido(ModoIa.API_KEY,
                new PedidoRespostas(null, "anthropic", null, null, false), EMB_PADRAO), "admin", "Bearer t"))
                .isInstanceOfSatisfying(ConfiguracaoIaRecusadaException.class, e -> assertThat(e.motivos())
                        .containsExactly("O modo API_KEY exige a chave de API do condomínio: informe a chave."));
    }

    @Test
    void apiKeySemProvedorOuComProvedorEModeloForaDoCatalogoEhRecusado() {
        assertThatThrownBy(() -> servico.gravar(CONDOMINIO, new Pedido(ModoIa.MCP_EXTERNO,
                new PedidoRespostas(ModoIa.API_KEY, null, null, CHAVE, false), EMB_PADRAO), "admin", "Bearer t"))
                .isInstanceOfSatisfying(ConfiguracaoIaRecusadaException.class,
                        e -> assertThat(e.motivos()).anyMatch(m -> m.contains("escolha o provedor das respostas")));
        assertThatThrownBy(() -> servico.gravar(CONDOMINIO, new Pedido(ModoIa.MCP_EXTERNO,
                new PedidoRespostas(ModoIa.API_KEY, "openai", null, CHAVE, false), EMB_PADRAO), "admin", "Bearer t"))
                .isInstanceOfSatisfying(ConfiguracaoIaRecusadaException.class,
                        e -> assertThat(e.motivos()).containsExactly("O provedor 'openai' não está no catálogo."));
        assertThatThrownBy(() -> servico.gravar(CONDOMINIO, new Pedido(ModoIa.MCP_EXTERNO,
                new PedidoRespostas(ModoIa.API_KEY, "anthropic", "gpt-9", CHAVE, false), EMB_PADRAO), "admin",
                "Bearer t"))
                .isInstanceOfSatisfying(ConfiguracaoIaRecusadaException.class, e -> assertThat(e.motivos())
                        .containsExactly("O modelo 'gpt-9' não está no catálogo do provedor 'anthropic'."));
        assertThatThrownBy(() -> servico.gravar(CONDOMINIO, new Pedido(ModoIa.MCP_EXTERNO,
                new PedidoRespostas(ModoIa.API_KEY, "ollama-local", null, CHAVE, false), EMB_PADRAO), "admin",
                "Bearer t"))
                .isInstanceOfSatisfying(ConfiguracaoIaRecusadaException.class,
                        e -> assertThat(e.motivos()).containsExactly("O provedor 'ollama-local' não é de respostas."));
    }

    /** RF-09.6: modo DESLIGADO com embeddings de provedor externo = recusa; com provedor local = aceita. */
    @Test
    void embeddingsSoComProvedorLocal() {
        assertThatThrownBy(() -> servico.gravar(CONDOMINIO, new Pedido(ModoIa.DESLIGADO,
                new PedidoRespostas(null, null, null, null, false), new PedidoEmbeddings(ModoIa.LOCAL, "voyage", null)),
                "admin", "Bearer t"))
                .isInstanceOfSatisfying(ConfiguracaoIaRecusadaException.class, e -> assertThat(e.motivos())
                        .containsExactly("O provedor 'voyage' não é local: para embeddings só é aceito provedor local,"
                                + " sem enviar texto para fora (Q12)."));

        var e = servico.gravar(CONDOMINIO, new Pedido(ModoIa.DESLIGADO, new PedidoRespostas(null, null, null, null,
                false), new PedidoEmbeddings(ModoIa.LOCAL, "ollama-local", "bge-m3")), "admin", "Bearer t");
        assertThat(e.embeddings().modo()).isEqualTo(ModoIa.LOCAL);
    }

    @Test
    void modoGeralLocalEChaveForaDoTamanhoSaoRecusados() {
        assertThatThrownBy(() -> servico.gravar(CONDOMINIO, new Pedido(ModoIa.LOCAL,
                new PedidoRespostas(ModoIa.DESLIGADO, null, null, "curta", false), EMB_PADRAO), "admin", "Bearer t"))
                .isInstanceOfSatisfying(ConfiguracaoIaRecusadaException.class, e -> assertThat(e.motivos())
                        .anyMatch(m -> m.startsWith("O modo geral LOCAL"))
                        .anyMatch(m -> m.contains("de 8 a 500 caracteres"))
                        .noneMatch(m -> m.contains("curta")));
    }

    @Test
    void ragForaDoArOuSemChavePublicaNaoGravaNada() {
        doThrow(Status.UNAVAILABLE.asRuntimeException()).when(rag).listarProvedores(anyString());
        assertThatThrownBy(() -> servico.gravar(CONDOMINIO, new Pedido(ModoIa.MCP_EXTERNO,
                new PedidoRespostas(ModoIa.API_KEY, "anthropic", null, CHAVE, false), EMB_PADRAO), "admin", "Bearer t"))
                .isInstanceOf(IaIndisponivelException.class);

        doReturn(CatalogoTeste.resposta("")).when(rag).listarProvedores(anyString());
        assertThatThrownBy(() -> servico.gravar(CONDOMINIO, new Pedido(ModoIa.MCP_EXTERNO,
                new PedidoRespostas(ModoIa.API_KEY, "anthropic", null, CHAVE, false), EMB_PADRAO), "admin", "Bearer t"))
                .isInstanceOf(IaIndisponivelException.class).hasMessageContaining("sem chave pública");
        assertThat(linhas).isEmpty();
        assertThat(trilha).isEmpty();
    }

    @Test
    void pedidoIncompletoEh400() {
        assertThatThrownBy(() -> servico.gravar(CONDOMINIO, new Pedido(null, null, EMB_PADRAO), "admin", "Bearer t"))
                .isInstanceOf(PedidoInvalidoException.class);
    }

    @Test
    void contextoNuloComModuloDesligadoEChatSoComApiKeyEChave() {
        when(modulos.ligado(CONDOMINIO, Modulos.ASSISTENTE)).thenReturn(false);
        assertThat(servico.contexto(CONDOMINIO)).isNull();

        when(modulos.ligado(CONDOMINIO, Modulos.ASSISTENTE)).thenReturn(true);
        assertThat(servico.contexto(CONDOMINIO)).isEqualTo(
                new ConfiguracaoIaServico.ContextoAssistente(ModoIa.MCP_EXTERNO, ModoIa.LOCAL, false));

        servico.gravar(CONDOMINIO, new Pedido(ModoIa.MCP_EXTERNO,
                new PedidoRespostas(ModoIa.API_KEY, "anthropic", null, CHAVE, false), EMB_PADRAO), "admin", "Bearer t");
        assertThat(servico.contexto(CONDOMINIO)).isEqualTo(
                new ConfiguracaoIaServico.ContextoAssistente(ModoIa.API_KEY, ModoIa.LOCAL, true));
    }

    @Test
    void pedidoNuncaMostraAChaveNoToString() {
        assertThat(new PedidoRespostas(ModoIa.API_KEY, "anthropic", null, CHAVE, false).toString())
                .doesNotContain(CHAVE).contains("chave enviada");
    }

    private List<String> textosDaTrilha() {
        List<String> textos = new ArrayList<>();
        for (EventoConfiguracaoIa ev : trilha) {
            for (Field f : EventoConfiguracaoIa.class.getDeclaredFields()) {
                f.setAccessible(true);
                try {
                    Object v = f.get(ev);
                    if (v != null) {
                        textos.add(v.toString());
                    }
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return textos;
    }
}
