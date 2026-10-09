package br.com.condominioauditoria.api.assistente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.condominioauditoria.api.assistente.DtosAssistente.PedidoBuscaDocumentos;
import br.com.condominioauditoria.api.assistente.DtosAssistente.PedidoPergunta;
import br.com.condominioauditoria.api.exception.FeatureNotEnabledException;
import br.com.condominioauditoria.api.ia.ConfiguracaoIaServico;
import br.com.condominioauditoria.api.ia.ConfiguracaoIaServico.Efetiva;
import br.com.condominioauditoria.api.ia.ConfiguracaoIaServico.Embeddings;
import br.com.condominioauditoria.api.ia.ConfiguracaoIaServico.Respostas;
import br.com.condominioauditoria.api.model.enums.AiMode;
import br.com.condominioauditoria.api.model.enums.FileCategory;
import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import br.com.condominioauditoria.api.repository.file.SourceFileRepository;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.feature.FeatureService;
import br.com.condominioauditoria.api.service.usage.UsageService;
import br.com.condominioauditoria.contratos.assistente.v1.BuscarResponse;
import br.com.condominioauditoria.contratos.assistente.v1.LocalPlanilha;
import br.com.condominioauditoria.contratos.assistente.v1.Localizacao;
import br.com.condominioauditoria.contratos.assistente.v1.ModoBusca;
import br.com.condominioauditoria.contratos.assistente.v1.Trecho;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

/**
 * API do assistente: perfis com a segurança por método real (USUARIO, GESTOR e ADMIN perguntam e buscam no próprio
 * condomínio) e respostas HTTP pelo tratador de erros real (403 módulo, 409 com modoIa sem chamar o rag, 200).
 */
class AssistenteControllerTest {

    private static final UUID A = UUID.randomUUID();

    private final SourceFileRepository arquivos = mock(SourceFileRepository.class);
    private final FeatureService modulos = mock(FeatureService.class);
    private final ConfiguracaoIaServico configuracao = mock(ConfiguracaoIaServico.class);
    private final UsageService registroUso = mock(UsageService.class);
    private final CondominiumRepository condominios = mock(CondominiumRepository.class);
    private final SourceFile planilha = new SourceFile(A, FileCategory.PO, "po.xlsx", "a/po.xlsx", "d".repeat(64), 1,
            "application/vnd.ms-excel", "gestor");
    private RagFalso rag;
    private AnnotationConfigApplicationContext contexto;
    private AssistenteController controller;
    private MockMvc mvc;

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        AssistenteController assistenteController(PerguntaAssistente pergunta, BuscaAssistente busca,
                CondominiumAccess acesso, CondominiumRepository condominios) {
            return new AssistenteController(pergunta, busca, acesso, condominios);
        }
    }

    @BeforeEach
    void subir() throws Exception {
        rag = new RagFalso();
        when(condominios.existsById(A)).thenReturn(true);
        when(arquivos.findByCondominiumIdAndIdIn(eq(A), anyCollection())).thenReturn(List.of(planilha));
        when(configuracao.ler(A)).thenReturn(new Efetiva(AiMode.MCP_EXTERNO,
                new Respostas(null, AiMode.MCP_EXTERNO, null, null, null, null),
                new Embeddings(AiMode.LOCAL, "ollama-local", "bge-m3"), null, null));
        var acesso = new CondominiumAccess();
        var barreira = new BarreiraArquivos(arquivos);
        var pergunta = new PerguntaAssistente(acesso, modulos, configuracao, rag.cliente, barreira, registroUso, 6);
        var busca = new BuscaAssistente(acesso, modulos, rag.cliente, barreira, registroUso);
        contexto = new AnnotationConfigApplicationContext();
        contexto.registerBean(PerguntaAssistente.class, () -> pergunta);
        contexto.registerBean(BuscaAssistente.class, () -> busca);
        contexto.registerBean(CondominiumAccess.class, () -> acesso);
        contexto.registerBean(CondominiumRepository.class, () -> condominios);
        contexto.register(Config.class);
        contexto.refresh();
        controller = contexto.getBean(AssistenteController.class);

        var construtor = Class.forName("br.com.condominioauditoria.api.exception.GlobalExceptionHandler")
                .getDeclaredConstructor();
        construtor.setAccessible(true);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(construtor.newInstance()).build();
    }

    @AfterEach
    void descer() {
        contexto.close();
        rag.close();
        SecurityContextHolder.clearContext();
    }

    @Test
    void todosOsPerfisBuscamPorPalavraNoProprioCondominioERegistramUso() {
        rag.aoBuscar = (p, r) -> {
            r.onNext(BuscarResponse.newBuilder().setModoUsado(ModoBusca.MODO_BUSCA_PALAVRA)
                    .addTrechos(Trecho.newBuilder().setTrechoId("t1").setArquivoId(planilha.getId().toString())
                            .setNomeArquivo("po.xlsx").setCategoria("PO").setTexto("Portão 12.000,00")
                            .setSha256(planilha.getSha256())
                            .setLocalizacao(Localizacao.newBuilder().setPlanilha(LocalPlanilha.newBuilder()
                                    .setAba("Junho").setLinhaInicio(10).setLinhaFim(14))))
                    .addTrechos(Trecho.newBuilder().setTrechoId("t2").setArquivoId(UUID.randomUUID().toString()))
                    .build());
            r.onCompleted();
        };
        for (String perfil : List.of("USUARIO", "GESTOR", "ADMIN")) {
            PerguntaAssistenteTest.logar(perfil, A);
            var trechos = controller.buscar(A, new PedidoBuscaDocumentos("  portão ", null, null));
            assertThat(trechos).singleElement().satisfies(t -> {
                assertThat(t.categoria()).isEqualTo(FileCategory.PO);
                assertThat(t.localizacao().tipo()).isEqualTo("PLANILHA");
                assertThat(t.localizacao().descricao()).isEqualTo("aba Junho, linhas 10 a 14");
            });
            verify(registroUso).recordDocumentSearch(A, "pessoa." + perfil.toLowerCase());
        }
        assertThat(rag.buscas).allSatisfy(b -> {
            assertThat(b.getModo()).isEqualTo(ModoBusca.MODO_BUSCA_PALAVRA);
            assertThat(b.getTexto()).isEqualTo("portão");
            assertThat(b.getLimite()).isEqualTo(10);
        });
    }

    @Test
    void semAcessoAoCondominioNaoPerguntaNemBusca() {
        PerguntaAssistenteTest.logar("GESTOR", A);
        UUID outro = UUID.randomUUID();
        assertThatThrownBy(() -> controller.perguntar(outro, new PedidoPergunta("x", null, null)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.buscar(outro, new PedidoBuscaDocumentos("x", null, null)))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(rag.perguntas).isEmpty();
        assertThat(rag.buscas).isEmpty();
    }

    @Test
    void condominioInexistenteEh404() {
        PerguntaAssistenteTest.logar("ADMIN", A);
        UUID outro = UUID.randomUUID();
        assertThatThrownBy(() -> controller.buscar(outro, new PedidoBuscaDocumentos("x", null, null)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
    }

    @Test
    void limiteForaDeUmACinquentaEh400() throws Exception {
        PerguntaAssistenteTest.logar("USUARIO", A);
        mvc.perform(post("/api/condominios/{id}/assistente/busca", A).contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\":\"portão\",\"limite\":51}"))
                .andExpect(status().isBadRequest());
        assertThat(rag.buscas).isEmpty();
    }

    @Test
    void moduloDesligadoEh403ComOCodigoDoModulo() throws Exception {
        PerguntaAssistenteTest.logar("USUARIO", A);
        doThrow(new FeatureNotEnabledException(FeatureService.ASSISTANT, "Assistente")).when(modulos)
                .require(A, FeatureService.ASSISTANT);

        mvc.perform(post("/api/condominios/{id}/assistente/perguntas", A).contentType(MediaType.APPLICATION_JSON)
                .content("{\"pergunta\":\"o portão foi aprovado?\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Módulo Assistente não contratado para este condomínio."))
                .andExpect(jsonPath("$.modulo").value("ASSISTENTE"));
        mvc.perform(post("/api/condominios/{id}/assistente/busca", A).contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\":\"portão\"}"))
                .andExpect(status().isForbidden());
        assertThat(rag.perguntas).isEmpty();
        assertThat(rag.buscas).isEmpty();
        verifyNoInteractions(registroUso);
    }

    @Test
    void mcpExternoEh409ComModoIaSemChamarORag() throws Exception {
        PerguntaAssistenteTest.logar("USUARIO", A);

        mvc.perform(post("/api/condominios/{id}/assistente/perguntas", A).contentType(MediaType.APPLICATION_JSON)
                .content("{\"pergunta\":\"o portão foi aprovado?\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("O assistente deste condomínio é o seu Claude, conectado ao MCP."))
                .andExpect(jsonPath("$.modoIa").value("MCP_EXTERNO"));
        assertThat(rag.perguntas).isEmpty();
        verify(registroUso, org.mockito.Mockito.never()).recordQuestion(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(), any());
    }

    @Test
    void buscaFuncionaComAIaDesligada() throws Exception {
        when(configuracao.ler(A)).thenReturn(new Efetiva(AiMode.DESLIGADO,
                new Respostas(null, AiMode.DESLIGADO, null, null, null, null),
                new Embeddings(AiMode.DESLIGADO, null, null), null, null));
        PerguntaAssistenteTest.logar("USUARIO", A);

        mvc.perform(post("/api/condominios/{id}/assistente/busca", A).contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\":\"portão\",\"filtros\":{\"categorias\":[\"PO\"]}}"))
                .andExpect(status().isOk());
        assertThat(rag.buscas).singleElement()
                .satisfies(b -> assertThat(b.getFiltros().getCategoriasList()).containsExactly("PO"));
    }
}
