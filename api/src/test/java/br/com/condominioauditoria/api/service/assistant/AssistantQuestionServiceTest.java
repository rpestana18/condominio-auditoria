package br.com.condominioauditoria.api.service.assistant;

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

import br.com.condominioauditoria.api.dto.request.assistant.ConversationTurnRequest;
import br.com.condominioauditoria.api.dto.request.assistant.DocumentFiltersRequest;
import br.com.condominioauditoria.api.dto.request.assistant.QuestionRequest;
import br.com.condominioauditoria.api.exception.AssistantRejectedException;
import br.com.condominioauditoria.api.exception.FeatureNotEnabledException;
import br.com.condominioauditoria.api.exception.InvalidRequestException;
import br.com.condominioauditoria.api.grpc.client.FakeRag;
import br.com.condominioauditoria.api.model.enums.AiMode;
import br.com.condominioauditoria.api.model.enums.AnswerStatus;
import br.com.condominioauditoria.api.model.enums.FileCategory;
import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.repository.file.SourceFileRepository;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.ai.AiConfigurationService;
import br.com.condominioauditoria.api.service.ai.AiConfigurationService.Answers;
import br.com.condominioauditoria.api.service.ai.AiConfigurationService.Effective;
import br.com.condominioauditoria.api.service.ai.AiConfigurationService.Embeddings;
import br.com.condominioauditoria.api.service.feature.FeatureService;
import br.com.condominioauditoria.api.service.usage.UsageService;
import br.com.condominioauditoria.contracts.assistant.v2.Progress;
import br.com.condominioauditoria.contracts.assistant.v2.StoredData;
import br.com.condominioauditoria.contracts.assistant.v2.AskStage;
import br.com.condominioauditoria.contracts.assistant.v2.DataRow;
import br.com.condominioauditoria.contracts.assistant.v2.PageLocation;
import br.com.condominioauditoria.contracts.assistant.v2.ChunkLocation;
import br.com.condominioauditoria.contracts.assistant.v2.SearchMode;
import br.com.condominioauditoria.contracts.assistant.v2.DocumentParagraph;
import br.com.condominioauditoria.contracts.assistant.v2.AskEvent;
import br.com.condominioauditoria.contracts.assistant.v2.Answer;
import br.com.condominioauditoria.contracts.assistant.v2.IndexedChunk;
import br.com.condominioauditoria.contracts.assistant.v2.AskUsage;
import io.grpc.Status;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Chat question (RF-04.8 to 04.16) with an in-process fake rag: rejections by mode without calling the rag,
 * configuration resolved in the request, limited history, second barrier with renumbering, usage record and error
 * translation.
 */
public class AssistantQuestionServiceTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final byte[] ENCRYPTED_KEY = {1, 2, 3, 4};

    private final SourceFileRepository files = mock(SourceFileRepository.class);
    private final FeatureService features = mock(FeatureService.class);
    private final AiConfigurationService aiConfiguration = mock(AiConfigurationService.class);
    private final UsageService usage = mock(UsageService.class);
    private final SourceFile minutesA = new SourceFile(A, FileCategory.MINUTES, "ata.pdf", "a/ata.pdf", "a".repeat(64), 1,
            "application/pdf", "gestor");
    private final SourceFile contractA = new SourceFile(A, FileCategory.CONTRACT, "contrato.pdf", "a/contrato.pdf",
            "c".repeat(64), 1, "application/pdf", "gestor");
    private final SourceFile minutesB = new SourceFile(B, FileCategory.MINUTES, "ata-b.pdf", "b/ata.pdf", "b".repeat(64), 1,
            "application/pdf", "gestor");
    private FakeRag rag;
    private AssistantQuestionService questions;

    @BeforeEach
    void setUp() throws Exception {
        rag = new FakeRag();
        when(files.findByCondominiumIdAndIdIn(eq(A), anyCollection())).thenAnswer(i -> {
            var ids = i.<java.util.Collection<UUID>>getArgument(1);
            return List.of(minutesA, contractA, minutesB).stream()
                    .filter(a -> a.getCondominiumId().equals(A) && ids.contains(a.getId())).toList();
        });
        configure(AiMode.API_KEY, ENCRYPTED_KEY, AiMode.LOCAL);
        questions = new AssistantQuestionService(new CondominiumAccess(), features, aiConfiguration, rag.client,
                new FileAccessBarrier(files), usage, 6);
        logIn("USER");
    }

    @AfterEach
    void tearDown() {
        rag.close();
        SecurityContextHolder.clearContext();
    }

    @Test
    void externalMcpIs409WithRf0416MessageWithoutCallingRag() {
        configure(AiMode.EXTERNAL_MCP, null, AiMode.LOCAL);

        assertThatThrownBy(() -> questions.ask(A, request("qual o índice de reajuste?")))
                .isInstanceOfSatisfying(AssistantRejectedException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).isEqualTo("O assistente deste condomínio é o seu Claude, conectado ao MCP.");
                    assertThat(e.aiMode()).isEqualTo(AiMode.EXTERNAL_MCP);
                });
        assertThat(rag.questions).isEmpty();
        verifyNoInteractions(usage);
    }

    @Test
    void offAndWithoutKeyAre409WithoutCallingRag() {
        configure(AiMode.OFF, null, AiMode.OFF);
        assertThatThrownBy(() -> questions.ask(A, request("x")))
                .isInstanceOfSatisfying(AssistantRejectedException.class, e -> {
                    assertThat(e.getMessage()).isEqualTo("A IA está desligada neste condomínio.");
                    assertThat(e.aiMode()).isEqualTo(AiMode.OFF);
                });

        configure(AiMode.API_KEY, null, AiMode.LOCAL);
        assertThatThrownBy(() -> questions.ask(A, request("x")))
                .isInstanceOfSatisfying(AssistantRejectedException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).contains("não está cadastrada");
                    assertThat(e.aiMode()).isEqualTo(AiMode.API_KEY);
                });
        assertThat(rag.questions).isEmpty();
    }

    @Test
    void featureOffRejectsBeforeAnything() {
        doThrow(new FeatureNotEnabledException(FeatureService.ASSISTANT, "Assistente")).when(features)
                .require(A, FeatureService.ASSISTANT);

        assertThatThrownBy(() -> questions.ask(A, request("x")))
                .isInstanceOf(FeatureNotEnabledException.class)
                .hasMessage("Módulo Assistente não contratado para este condomínio.");
        assertThat(rag.questions).isEmpty();
        verifyNoInteractions(aiConfiguration, usage);
    }

    @Test
    void emptyOrTooLongQuestionIs400() {
        assertThatThrownBy(() -> questions.ask(A, request("   "))).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> questions.ask(A, request("x".repeat(2001))))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> questions.ask(A, new QuestionRequest("x", null, new DocumentFiltersRequest(null,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 1, 1),
                        null)))).isInstanceOf(InvalidRequestException.class);
        assertThat(rag.questions).isEmpty();
    }

    @Test
    void sendsResolvedConfigurationTokenFiltersAndLastSixTurns() {
        respond(Answer.newBuilder().setOutcome(
                br.com.condominioauditoria.contracts.assistant.v2.AnswerOutcome.ANSWER_OUTCOME_NOT_FOUND)
                .setUsage(questionUsage(10, 2)).build());
        List<ConversationTurnRequest> history = IntStream.rangeClosed(1, 8)
                .mapToObj(i -> new ConversationTurnRequest("p" + i, "r" + i)).toList();

        questions.ask(A, new QuestionRequest("  e o de portaria?  ", history, new DocumentFiltersRequest(
                List.of(FileCategory.CONTRACT), LocalDate.of(2025, 1, 1), null, List.of(contractA.getId()))));

        assertThat(rag.token.get()).isEqualTo("Bearer token-user");
        var request = rag.questions.getFirst();
        assertThat(request.getCondominiumId()).isEqualTo(A.toString());
        assertThat(request.getQuestion()).isEqualTo("e o de portaria?");
        assertThat(request.getHistoryList()).extracting(t -> t.getQuestion())
                .containsExactly("p3", "p4", "p5", "p6", "p7", "p8");
        assertThat(request.getConfiguration().getProvider()).isEqualTo("anthropic");
        assertThat(request.getConfiguration().getModel()).isEqualTo("claude-sonnet-5-5");
        assertThat(request.getConfiguration().getEncryptedKey().toByteArray()).isEqualTo(ENCRYPTED_KEY);
        assertThat(request.getConfiguration().getEmbeddingModel()).isEqualTo("bge-m3");
        assertThat(request.getConfiguration().getSearchMode()).isEqualTo(SearchMode.SEARCH_MODE_HYBRID);
        assertThat(request.getFilters().getCategoriesList()).containsExactly("CONTRACT");
        assertThat(request.getFilters().getDateFrom()).isEqualTo("2025-01-01");
        assertThat(request.getFilters().getDateTo()).isEmpty();
        assertThat(request.getFilters().getFileIdsList()).containsExactly(contractA.getId().toString());
    }

    @Test
    void embeddingsOffAskForWordSearch() {
        configure(AiMode.API_KEY, ENCRYPTED_KEY, AiMode.OFF);
        respond(Answer.newBuilder().setOutcome(
                br.com.condominioauditoria.contracts.assistant.v2.AnswerOutcome.ANSWER_OUTCOME_NOT_FOUND)
                .build());

        questions.ask(A, request("x"));

        assertThat(rag.questions.getFirst().getConfiguration().getSearchMode()).isEqualTo(SearchMode.SEARCH_MODE_KEYWORD);
        assertThat(rag.questions.getFirst().getConfiguration().getEmbeddingModel()).isEmpty();
    }

    @Test
    void secondBarrierDropsOtherCondominiumFileRenumbersAndRemovesUncitedParagraph() {
        IndexedChunk fromMinutesB = chunk("t-b", minutesB, 2);
        IndexedChunk fromMinutes = chunk("t-ata", minutesA, 3);
        IndexedChunk fromContract = chunk("t-contrato", contractA, 4);
        IndexedChunk missing = IndexedChunk.newBuilder().setChunkId("t-x").setFileId(UUID.randomUUID().toString()).build();
        respond(Answer.newBuilder()
                .setOutcome(br.com.condominioauditoria.contracts.assistant.v2.AnswerOutcome.ANSWER_OUTCOME_ANSWERED)
                .addInDocuments(DocumentParagraph.newBuilder().setText("Só do B.").addChunkIds("t-b"))
                .addInDocuments(DocumentParagraph.newBuilder().setText("A ata aprovou.").addChunkIds("t-b")
                        .addChunkIds("t-ata"))
                .addInDocuments(DocumentParagraph.newBuilder().setText("O contrato prevê IPCA.")
                        .addChunkIds("t-contrato").addChunkIds("t-x"))
                .addInStoredData(StoredData.newBuilder().setCallId("c1").setQuery("fund_summary")
                        .addRows(DataRow.newBuilder().setLabel("Saldo").setValue("R$ 1.234,56")))
                .addCitedChunks(fromMinutesB).addCitedChunks(fromMinutes).addCitedChunks(fromContract)
                .addCitedChunks(missing)
                .setSuggestion("").setWarning("Busca só por palavra.")
                .setUsage(questionUsage(1200, 340))
                .build());

        var response = questions.ask(A, request("a troca do portão foi aprovada e quanto foi pago?"));

        assertThat(response.status()).isEqualTo(AnswerStatus.ANSWERED);
        assertThat(response.fromDocuments()).extracting(p -> p.text())
                .containsExactly("A ata aprovou.", "O contrato prevê IPCA.");
        assertThat(response.fromDocuments()).extracting(p -> p.citations())
                .containsExactly(List.of(1), List.of(2));
        assertThat(response.citations()).extracting(c -> c.number(), c -> c.fileId())
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1, minutesA.getId()),
                        org.assertj.core.groups.Tuple.tuple(2, contractA.getId()));
        assertThat(response.citations().getFirst().location().description()).isEqualTo("página 3");
        assertThat(response.citations().getFirst().sha256()).isEqualTo(minutesA.getSha256());
        assertThat(response.fromStoredData()).singleElement().satisfies(d -> {
            assertThat(d.query()).isEqualTo("fund_summary");
            assertThat(d.rows().getFirst().value()).isEqualTo("R$ 1.234,56");
            assertThat(d.comment()).isNull();
        });
        assertThat(response.suggestion()).isNull();
        assertThat(response.warning()).isEqualTo("Busca só por palavra.");
        assertThat(response.model()).isEqualTo("claude-sonnet-5-5");
        verify(usage).recordQuestion(A, "pessoa.user", "anthropic", "claude-sonnet-5-5", 1200, 340, "2026-10-05.1");
    }

    @Test
    void nothingLeftBecomesNotFoundAndStillRecordsUsage() {
        respond(Answer.newBuilder()
                .setOutcome(br.com.condominioauditoria.contracts.assistant.v2.AnswerOutcome.ANSWER_OUTCOME_ANSWERED)
                .addInDocuments(DocumentParagraph.newBuilder().setText("Do B.").addChunkIds("t-b"))
                .addCitedChunks(chunk("t-b", minutesB, 1))
                .setSuggestion("não há ata de 2025 enviada")
                .setUsage(questionUsage(900, 100))
                .build());

        var response = questions.ask(A, request("o portão foi aprovado?"));

        assertThat(response.status()).isEqualTo(AnswerStatus.NOT_FOUND);
        assertThat(response.fromDocuments()).isEmpty();
        assertThat(response.citations()).isEmpty();
        assertThat(response.fromStoredData()).isEmpty();
        assertThat(response.suggestion()).isNull();
        verify(usage).recordQuestion(A, "pessoa.user", "anthropic", "claude-sonnet-5-5", 900, 100, "2026-10-05.1");
    }

    @Test
    void ragNotFoundPassesWithSuggestion() {
        respond(Answer.newBuilder()
                .setOutcome(br.com.condominioauditoria.contracts.assistant.v2.AnswerOutcome.ANSWER_OUTCOME_NOT_FOUND)
                .setSuggestion("não há contrato de jardinagem enviado").setUsage(questionUsage(500, 50)).build());

        var response = questions.ask(A, request("qual a empresa de jardinagem?"));

        assertThat(response.status()).isEqualTo(AnswerStatus.NOT_FOUND);
        assertThat(response.suggestion()).isEqualTo("não há contrato de jardinagem enviado");
        verify(usage).recordQuestion(any(), any(), any(), any(), eq(500L), eq(50L), any());
    }

    @Test
    void ragErrorsBecomeContractStatusesWithoutRecordingUsage() {
        Map<Status, HttpStatus> cases = Map.of(
                Status.FAILED_PRECONDITION.withDescription("A chave de IA deste condomínio não pôde ser lida; cadastre"
                        + " de novo"), HttpStatus.CONFLICT,
                Status.PERMISSION_DENIED.withDescription("chave recusada"), HttpStatus.UNPROCESSABLE_CONTENT,
                Status.RESOURCE_EXHAUSTED, HttpStatus.TOO_MANY_REQUESTS,
                Status.UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                Status.DEADLINE_EXCEEDED, HttpStatus.GATEWAY_TIMEOUT,
                Status.UNIMPLEMENTED, HttpStatus.SERVICE_UNAVAILABLE,
                Status.INVALID_ARGUMENT.withDescription("pergunta acima de 2000 caracteres"), HttpStatus.BAD_REQUEST);
        List<String> errors = new ArrayList<>();
        cases.forEach((status, http) -> {
            rag.onAsk = (p, r) -> r.onError(status.asRuntimeException());
            assertThatThrownBy(() -> questions.ask(A, request("x")))
                    .isInstanceOfSatisfying(AssistantRejectedException.class, e -> {
                        assertThat(e.status()).as(status.getCode().name()).isEqualTo(http);
                        errors.add(e.getMessage());
                    });
        });
        assertThat(errors).anyMatch(m -> m.contains("cadastre"));
        assertThat(errors).anyMatch(m -> m.contains("120") || m.contains("7 segundos"));
        verifyNoInteractions(usage);
    }

    @Test
    void ragDownIs503() {
        rag.shutDown();

        assertThatThrownBy(() -> questions.ask(A, request("x")))
                .isInstanceOfSatisfying(AssistantRejectedException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verifyNoInteractions(usage);
    }

    // ---- apoio ----

    private void configure(AiMode answersMode, byte[] key, AiMode embeddingsMode) {
        var answers = new Answers(answersMode, answersMode, key == null ? null : "anthropic",
                key == null ? null : "claude-sonnet-5-5", key, key == null ? null : "x9Qa");
        var embeddings = embeddingsMode == AiMode.LOCAL ? new Embeddings(AiMode.LOCAL, "ollama-local", "bge-m3")
                : new Embeddings(embeddingsMode, null, null);
        when(aiConfiguration.read(A)).thenReturn(new Effective(AiMode.EXTERNAL_MCP, answers, embeddings, null, null));
    }

    private void respond(Answer response) {
        rag.onAsk = (p, r) -> {
            r.onNext(AskEvent.newBuilder().setProgress(Progress.newBuilder()
                    .setStage(AskStage.ASK_STAGE_SEARCHING_CHUNKS).setAttempt(1)).build());
            r.onNext(AskEvent.newBuilder().setAnswer(response).build());
            r.onCompleted();
        };
    }

    private static AskUsage questionUsage(long input, long output) {
        return AskUsage.newBuilder().setInputTokens(input).setOutputTokens(output).setProvider("anthropic")
                .setModel("claude-sonnet-5-5").setPromptVersion("2026-10-05.1").setAttempts(1).build();
    }

    private static IndexedChunk chunk(String id, SourceFile file, int page) {
        return IndexedChunk.newBuilder().setChunkId(id).setFileId(file.getId().toString())
                .setFileName(file.getOriginalName()).setCategory(file.getCategory().name())
                .setLocation(ChunkLocation.newBuilder().setPage(PageLocation.newBuilder().setPage(page)))
                .setText("texto de " + file.getOriginalName()).setSha256(file.getSha256()).build();
    }

    private static QuestionRequest request(String text) {
        return new QuestionRequest(text, null, null);
    }

    public static void logIn(String role) {
        logIn(role, A);
    }

    public static void logIn(String role, UUID condominium) {
        Jwt jwt = new Jwt("token-" + role.toLowerCase(), Instant.now(), Instant.now().plusSeconds(300),
                Map.of("alg", "none"), Map.of("preferred_username", "pessoa." + role.toLowerCase(), "condominiums",
                        List.of(condominium.toString())));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)), "pessoa." + role.toLowerCase()));
    }
}
