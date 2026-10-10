package br.com.condominioauditoria.api.controller.assistant;

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

import br.com.condominioauditoria.api.dto.request.assistant.DocumentSearchRequest;
import br.com.condominioauditoria.api.dto.request.assistant.QuestionRequest;
import br.com.condominioauditoria.api.exception.FeatureNotEnabledException;
import br.com.condominioauditoria.api.grpc.client.FakeRag;
import br.com.condominioauditoria.api.model.enums.AiMode;
import br.com.condominioauditoria.api.model.enums.FileCategory;
import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import br.com.condominioauditoria.api.repository.file.SourceFileRepository;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.ai.AiConfigurationService;
import br.com.condominioauditoria.api.service.ai.AiConfigurationService.Answers;
import br.com.condominioauditoria.api.service.ai.AiConfigurationService.Effective;
import br.com.condominioauditoria.api.service.ai.AiConfigurationService.Embeddings;
import br.com.condominioauditoria.api.service.assistant.AssistantQuestionService;
import br.com.condominioauditoria.api.service.assistant.AssistantQuestionServiceTest;
import br.com.condominioauditoria.api.service.assistant.AssistantSearchService;
import br.com.condominioauditoria.api.service.assistant.FileAccessBarrier;
import br.com.condominioauditoria.api.service.condominium.CondominiumService;
import br.com.condominioauditoria.api.service.feature.FeatureService;
import br.com.condominioauditoria.api.service.usage.UsageService;
import br.com.condominioauditoria.contracts.assistant.v2.SearchResponse;
import br.com.condominioauditoria.contracts.assistant.v2.SheetLocation;
import br.com.condominioauditoria.contracts.assistant.v2.ChunkLocation;
import br.com.condominioauditoria.contracts.assistant.v2.SearchMode;
import br.com.condominioauditoria.contracts.assistant.v2.IndexedChunk;
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
 * Assistant API: roles with the real method security (USER, MANAGER and ADMIN ask and search in their own
 * condominium) and HTTP responses through the real error handler (403 feature, 409 with modoIa without calling the rag,
 * 200).
 */
class AssistantControllerTest {

    private static final UUID A = UUID.randomUUID();

    private final SourceFileRepository files = mock(SourceFileRepository.class);
    private final FeatureService features = mock(FeatureService.class);
    private final AiConfigurationService aiConfiguration = mock(AiConfigurationService.class);
    private final UsageService usage = mock(UsageService.class);
    private final CondominiumRepository condominiums = mock(CondominiumRepository.class);
    private final SourceFile workbook = new SourceFile(A, FileCategory.PO, "po.xlsx", "a/po.xlsx", "d".repeat(64), 1,
            "application/vnd.ms-excel", "gestor");
    private FakeRag rag;
    private AnnotationConfigApplicationContext context;
    private AssistantController controller;
    private MockMvc mvc;

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        AssistantController assistantController(AssistantQuestionService question, AssistantSearchService search,
                CondominiumAccess access, CondominiumService condominiums) {
            return new AssistantController(question, search, access, condominiums);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        rag = new FakeRag();
        when(condominiums.existsById(A)).thenReturn(true);
        when(files.findByCondominiumIdAndIdIn(eq(A), anyCollection())).thenReturn(List.of(workbook));
        when(aiConfiguration.read(A)).thenReturn(new Effective(AiMode.EXTERNAL_MCP,
                new Answers(null, AiMode.EXTERNAL_MCP, null, null, null, null),
                new Embeddings(AiMode.LOCAL, "ollama-local", "bge-m3"), null, null));
        var access = new CondominiumAccess();
        var barrier = new FileAccessBarrier(files);
        var question = new AssistantQuestionService(access, features, aiConfiguration, rag.client, barrier, usage, 6);
        var search = new AssistantSearchService(access, features, rag.client, barrier, usage);
        context = new AnnotationConfigApplicationContext();
        context.registerBean(AssistantQuestionService.class, () -> question);
        context.registerBean(AssistantSearchService.class, () -> search);
        context.registerBean(CondominiumAccess.class, () -> access);
        context.registerBean(CondominiumService.class,
                () -> new CondominiumService(condominiums, features, aiConfiguration));
        context.register(Config.class);
        context.refresh();
        controller = context.getBean(AssistantController.class);

        var builder = Class.forName("br.com.condominioauditoria.api.exception.GlobalExceptionHandler")
                .getDeclaredConstructor();
        builder.setAccessible(true);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(builder.newInstance()).build();
    }

    @AfterEach
    void tearDown() {
        context.close();
        rag.close();
        SecurityContextHolder.clearContext();
    }

    @Test
    void allRolesSearchByWordInOwnCondominiumAndRecordUsage() {
        rag.onSearch = (p, r) -> {
            r.onNext(SearchResponse.newBuilder().setModeUsed(SearchMode.SEARCH_MODE_KEYWORD)
                    .addChunks(IndexedChunk.newBuilder().setChunkId("t1").setFileId(workbook.getId().toString())
                            .setFileName("po.xlsx").setCategory("PO").setText("Portão 12.000,00")
                            .setSha256(workbook.getSha256())
                            .setLocation(ChunkLocation.newBuilder().setSheet(SheetLocation.newBuilder()
                                    .setTab("Junho").setStartRow(10).setEndRow(14))))
                    .addChunks(IndexedChunk.newBuilder().setChunkId("t2").setFileId(UUID.randomUUID().toString()))
                    .build());
            r.onCompleted();
        };
        for (String role : List.of("USER", "MANAGER", "ADMIN")) {
            AssistantQuestionServiceTest.logIn(role, A);
            var chunks = controller.search(A, new DocumentSearchRequest("  portão ", null, null));
            assertThat(chunks).singleElement().satisfies(t -> {
                assertThat(t.category()).isEqualTo(FileCategory.PO);
                assertThat(t.location().type()).isEqualTo("SHEET");
                assertThat(t.location().description()).isEqualTo("aba Junho, linhas 10 a 14");
            });
            verify(usage).recordDocumentSearch(A, "pessoa." + role.toLowerCase());
        }
        assertThat(rag.searches).allSatisfy(b -> {
            assertThat(b.getMode()).isEqualTo(SearchMode.SEARCH_MODE_KEYWORD);
            assertThat(b.getText()).isEqualTo("portão");
            assertThat(b.getLimit()).isEqualTo(10);
        });
    }

    @Test
    void withoutCondominiumAccessNeitherAsksNorSearches() {
        AssistantQuestionServiceTest.logIn("MANAGER", A);
        UUID other = UUID.randomUUID();
        assertThatThrownBy(() -> controller.ask(other, new QuestionRequest("x", null, null)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.search(other, new DocumentSearchRequest("x", null, null)))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(rag.questions).isEmpty();
        assertThat(rag.searches).isEmpty();
    }

    @Test
    void unknownCondominiumIs404() {
        AssistantQuestionServiceTest.logIn("ADMIN", A);
        UUID other = UUID.randomUUID();
        assertThatThrownBy(() -> controller.search(other, new DocumentSearchRequest("x", null, null)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
    }

    @Test
    void limitOutsideOneToFiftyIs400() throws Exception {
        AssistantQuestionServiceTest.logIn("USER", A);
        mvc.perform(post("/api/condominiums/{id}/assistant/search", A).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"portão\",\"limit\":51}"))
                .andExpect(status().isBadRequest());
        assertThat(rag.searches).isEmpty();
    }

    @Test
    void featureOffIs403WithFeatureCode() throws Exception {
        AssistantQuestionServiceTest.logIn("USER", A);
        doThrow(new FeatureNotEnabledException(FeatureService.ASSISTANT, "Assistente")).when(features)
                .require(A, FeatureService.ASSISTANT);

        mvc.perform(post("/api/condominiums/{id}/assistant/questions", A).contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"o portão foi aprovado?\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Módulo Assistente não contratado para este condomínio."))
                .andExpect(jsonPath("$.feature").value("ASSISTANT"));
        mvc.perform(post("/api/condominiums/{id}/assistant/search", A).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"portão\"}"))
                .andExpect(status().isForbidden());
        assertThat(rag.questions).isEmpty();
        assertThat(rag.searches).isEmpty();
        verifyNoInteractions(usage);
    }

    @Test
    void externalMcpIs409WithAiModeWithoutCallingRag() throws Exception {
        AssistantQuestionServiceTest.logIn("USER", A);

        mvc.perform(post("/api/condominiums/{id}/assistant/questions", A).contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"o portão foi aprovado?\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("O assistente deste condomínio é o seu Claude, conectado ao MCP."))
                .andExpect(jsonPath("$.aiMode").value("EXTERNAL_MCP"));
        assertThat(rag.questions).isEmpty();
        verify(usage, org.mockito.Mockito.never()).recordQuestion(any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(), any());
    }

    @Test
    void searchWorksWithAiOff() throws Exception {
        when(aiConfiguration.read(A)).thenReturn(new Effective(AiMode.OFF,
                new Answers(null, AiMode.OFF, null, null, null, null),
                new Embeddings(AiMode.OFF, null, null), null, null));
        AssistantQuestionServiceTest.logIn("USER", A);

        mvc.perform(post("/api/condominiums/{id}/assistant/search", A).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"portão\",\"filters\":{\"categories\":[\"PO\"]}}"))
                .andExpect(status().isOk());
        assertThat(rag.searches).singleElement()
                .satisfies(b -> assertThat(b.getFilters().getCategoriesList()).containsExactly("PO"));
    }
}
