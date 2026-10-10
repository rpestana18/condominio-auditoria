package br.com.condominioauditoria.api.service.assistant;

import br.com.condominioauditoria.api.dto.request.assistant.ConversationTurnRequest;
import br.com.condominioauditoria.api.dto.request.assistant.QuestionRequest;
import br.com.condominioauditoria.api.dto.response.assistant.AssistantAnswerResponse;
import br.com.condominioauditoria.api.dto.response.assistant.DataRowResponse;
import br.com.condominioauditoria.api.dto.response.assistant.DocumentCitationResponse;
import br.com.condominioauditoria.api.dto.response.assistant.DocumentParagraphResponse;
import br.com.condominioauditoria.api.dto.response.assistant.QueryParameterResponse;
import br.com.condominioauditoria.api.dto.response.assistant.StoredDataResponse;
import br.com.condominioauditoria.api.exception.AssistantRejectedException;
import br.com.condominioauditoria.api.exception.InvalidRequestException;
import br.com.condominioauditoria.api.grpc.client.AssistantClient;
import br.com.condominioauditoria.api.mapper.AssistantMapper;
import br.com.condominioauditoria.api.model.enums.AiMode;
import br.com.condominioauditoria.api.model.enums.AnswerStatus;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.ai.AiConfigurationService;
import br.com.condominioauditoria.api.service.ai.AiConfigurationService.Effective;
import br.com.condominioauditoria.api.service.feature.FeatureService;
import br.com.condominioauditoria.api.service.usage.UsageService;
import br.com.condominioauditoria.contracts.assistant.v2.AskConfiguration;
import br.com.condominioauditoria.contracts.assistant.v2.SearchFilters;
import br.com.condominioauditoria.contracts.assistant.v2.SearchMode;
import br.com.condominioauditoria.contracts.assistant.v2.AskRequest;
import br.com.condominioauditoria.contracts.assistant.v2.Answer;
import br.com.condominioauditoria.contracts.assistant.v2.IndexedChunk;
import br.com.condominioauditoria.contracts.assistant.v2.AskUsage;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Asks the Assistant chat (RF-04.8 to 04.16; ADR 0003, Decision 5.2). Who checks what:
 * <ol>
 * <li>api: access to the condominium (the role was already checked in the API), feature enabled, effective answers
 * mode API_KEY with a key. EXTERNAL_MCP, OFF, LOCAL or no key = 409 without calling the rag;</li>
 * <li>rag: search, model with tools (which call the api's Consulta with the same token) and validation;</li>
 * <li>api: second barrier on the citations, usage record and JSON response.</li>
 * </ol>
 * No surrounding transaction: the API thread waits for the rag (up to the timeout) without holding a database
 * connection, and the rag calls the api's Consulta on another thread pool.
 */
@Service
public class AssistantQuestionService {

    private static final Logger log = LoggerFactory.getLogger(AssistantQuestionService.class);

    static final int QUESTION_MAX_LENGTH = 2000;
    static final String UNAVAILABLE_TITLE = "Chat indisponível";
    static final String EXTERNAL_MCP_MESSAGE = "O assistente deste condomínio é o seu Claude, conectado ao MCP.";
    static final String AI_OFF_MESSAGE = "A IA está desligada neste condomínio.";
    static final String LOCAL_MESSAGE = "O modo LOCAL de respostas ainda não está disponível neste condomínio.";
    static final String NO_KEY_MESSAGE = "A chave de IA deste condomínio não está cadastrada; o Admin precisa"
            + " cadastrar a chave na configuração de IA.";
    static final String UNREADABLE_KEY_MESSAGE = "A chave de IA deste condomínio não pôde ser usada; cadastre a chave de"
            + " novo.";
    static final String REJECTED_KEY_MESSAGE = "A chave de IA do condomínio foi recusada pelo provedor. O Admin precisa"
            + " conferir a chave ou cadastrar outra.";
    static final String RATE_LIMIT_MESSAGE = "O limite de uso do provedor de IA foi atingido. Tente de novo mais tarde.";
    static final String UNAVAILABLE_MESSAGE = "O assistente está indisponível no momento (serviço rag ou provedor de IA"
            + " fora do ar). Tente de novo em instantes.";

    private final CondominiumAccess access;
    private final FeatureService features;
    private final AiConfigurationService aiConfiguration;
    private final AssistantClient rag;
    private final FileAccessBarrier barrier;
    private final UsageService usage;
    private final int historyTurns;

    public AssistantQuestionService(CondominiumAccess access, FeatureService features,
            AiConfigurationService aiConfiguration,
            AssistantClient rag, FileAccessBarrier barrier, UsageService usage,
            @Value("${condominio.assistente.historico-trocas:6}") int historyTurns) {
        this.access = access;
        this.features = features;
        this.aiConfiguration = aiConfiguration;
        this.rag = rag;
        this.barrier = barrier;
        this.usage = usage;
        this.historyTurns = Math.max(0, historyTurns);
    }

    /** Called by the API after checking role, access and that the condominium exists. */
    public AssistantAnswerResponse ask(UUID condominiumId, QuestionRequest request) {
        features.require(condominiumId, FeatureService.ASSISTANT);
        Effective config = aiConfiguration.read(condominiumId);
        requireChatAvailable(config);

        String question = request == null || request.question() == null ? "" : request.question().strip();
        if (question.isEmpty()) {
            throw new InvalidRequestException("Escreva a pergunta");
        }
        if (question.length() > QUESTION_MAX_LENGTH) {
            throw new InvalidRequestException("A pergunta passa de " + QUESTION_MAX_LENGTH + " caracteres");
        }
        AskRequest ragRequest = buildRequest(condominiumId, question, request, config);
        String authorization = access.bearerToken().orElseThrow(() -> new IllegalStateException("Token ausente"));

        Answer response;
        try {
            response = rag.ask(ragRequest, authorization);
        } catch (StatusRuntimeException error) {
            throw translate(error, rag.questionTimeoutSeconds());
        }

        AssistantAnswerResponse output = filter(condominiumId, response, config);
        AskUsage questionUsage = response.getUsage();
        usage.recordQuestion(condominiumId, access.username(),
                questionUsage.getProvider().isBlank() ? config.answers().provider() : questionUsage.getProvider(),
                questionUsage.getModel().isBlank() ? config.answers().model() : questionUsage.getModel(),
                        questionUsage.getInputTokens(),
                questionUsage.getOutputTokens(), questionUsage.getPromptVersion());
        return output;
    }

    /** Rejects (409, without calling the rag) when the effective answers mode is not API_KEY with a key (RF-04.16). */
    static void requireChatAvailable(Effective config) {
        AiMode mode = config.answers().effectiveMode();
        String message = switch (mode) {
            case EXTERNAL_MCP -> EXTERNAL_MCP_MESSAGE;
            case OFF -> AI_OFF_MESSAGE;
            case LOCAL -> LOCAL_MESSAGE;
            case API_KEY -> config.answers().chatAvailable() ? null : NO_KEY_MESSAGE;
        };
        if (message != null) {
            throw new AssistantRejectedException(HttpStatus.CONFLICT, UNAVAILABLE_TITLE, message, mode);
        }
    }

    AskRequest buildRequest(UUID condominiumId, String question, QuestionRequest request, Effective config) {
        var r = config.answers();
        var e = config.embeddings();
        var builder = AskRequest.newBuilder()
                .setCondominiumId(condominiumId.toString())
                .setQuestion(question)
                .setConfiguration(AskConfiguration.newBuilder()
                        .setProvider(r.provider())
                        .setModel(Objects.requireNonNullElse(r.model(), ""))
                        .setEncryptedKey(ByteString.copyFrom(r.encryptedKey()))
                        .setEmbeddingModel(e.mode() == AiMode.LOCAL ? Objects.requireNonNullElse(e.model(), "") : "")
                        .setSearchMode(e.mode() == AiMode.OFF ? SearchMode.SEARCH_MODE_KEYWORD
                                : SearchMode.SEARCH_MODE_HYBRID));
        List<ConversationTurnRequest> history = RagRequests.orEmpty(request.history()).stream().filter(Objects::nonNull).toList();
        for (ConversationTurnRequest t : history.subList(Math.max(0, history.size() - historyTurns), history.size())) {
            builder.addHistory(br.com.condominioauditoria.contracts.assistant.v2.PreviousTurn.newBuilder()
                    .setQuestion(Objects.requireNonNullElse(t.question(), ""))
                    .setAnswer(Objects.requireNonNullElse(t.answer(), "")));
        }
        SearchFilters filters = RagRequests.filters(request.filters());
        if (filters != null) {
            builder.setFilters(filters);
        }
        return builder.build();
    }

    /**
     * Second barrier: drops chunks of files that are not the condominium's or no longer exist, renumbers the citations
     * (1, 2, ... in the order of the cited chunks), removes a paragraph left without a citation and, if nothing is
     * left, answers NOT_FOUND.
     */
    AssistantAnswerResponse filter(UUID condominiumId, Answer response, Effective config) {
        String model = !response.getUsage().getModel().isBlank() ? response.getUsage().getModel()
                : Objects.requireNonNullElse(config.answers().model(), "");
        String suggestion = blankToNull(response.getSuggestion());
        String warning = blankToNull(response.getWarning());
        if (response.getOutcome() != br.com.condominioauditoria.contracts.assistant.v2.AnswerOutcome
                .ANSWER_OUTCOME_ANSWERED) {
            return notFound(suggestion, warning, model);
        }

        Set<String> visible = barrier.visibleIds(condominiumId, response.getCitedChunksList());
        Map<String, IndexedChunk> allowed = new LinkedHashMap<>();
        for (IndexedChunk t : response.getCitedChunksList()) {
            if (FileAccessBarrier.isAllowed(t, visible)) {
                allowed.putIfAbsent(t.getChunkId(), t);
            }
        }
        // Only the chunks some paragraph cites, in the order of trechos_citados
        Set<String> citedByParagraph = new java.util.HashSet<>();
        response.getInDocumentsList().forEach(p -> citedByParagraph.addAll(p.getChunkIdsList()));
        Map<String, Integer> numbers = new LinkedHashMap<>();
        List<DocumentCitationResponse> citations = new ArrayList<>();
        for (IndexedChunk t : allowed.values()) {
            if (citedByParagraph.contains(t.getChunkId())) {
                int number = numbers.size() + 1;
                numbers.put(t.getChunkId(), number);
                citations.add(AssistantMapper.toCitation(number, t));
            }
        }
        List<DocumentParagraphResponse> paragraphs = new ArrayList<>();
        int dropped = 0;
        for (var p : response.getInDocumentsList()) {
            List<Integer> nums = p.getChunkIdsList().stream().map(numbers::get).filter(Objects::nonNull).distinct()
                    .sorted().toList();
            if (nums.isEmpty()) {
                dropped++;
            } else {
                paragraphs.add(new DocumentParagraphResponse(p.getText(), nums));
            }
        }
        if (dropped > 0 || allowed.size() < response.getCitedChunksCount()) {
            log.warn("Pergunta ao assistente: {} trecho(s) e {} parágrafo(s) descartados pela segunda barreira"
                    + " (condomínio {})", response.getCitedChunksCount() - allowed.size(), dropped,
                    condominiumId);
        }
        List<StoredDataResponse> data = response.getInStoredDataList().stream()
                .map(d -> new StoredDataResponse(d.getQuery(),
                        d.getParametersList().stream().map(x -> new QueryParameterResponse(x.getName(),
                                x.getValue())).toList(),
                        d.getRowsList().stream().map(x -> new DataRowResponse(x.getLabel(), x.getValue())).toList(),
                        blankToNull(d.getComment())))
                .toList();
        if (paragraphs.isEmpty() && data.isEmpty()) {
            // The suggestion was written for the dropped answer; it no longer applies
            return notFound(null, warning, model);
        }
        return new AssistantAnswerResponse(AnswerStatus.ANSWERED, paragraphs, data, citations, suggestion, warning,
                model);
    }

    private static AssistantAnswerResponse notFound(String suggestion, String warning, String model) {
        return new AssistantAnswerResponse(AnswerStatus.NOT_FOUND, List.of(), List.of(), List.of(), suggestion,
                warning,
                model);
    }

    /**
     * rag gRPC status → the contract's HTTP status. The rag's description is already in Portuguese and without the key.
     */
    static AssistantRejectedException translate(StatusRuntimeException error, long timeoutSeconds) {
        Status status = error.getStatus();
        String description = status.getDescription();
        return switch (status.getCode()) {
            case INVALID_ARGUMENT -> new AssistantRejectedException(HttpStatus.BAD_REQUEST, "Pergunta inválida",
                    Objects.requireNonNullElse(description, "Pergunta inválida"), null);
            case FAILED_PRECONDITION -> new AssistantRejectedException(HttpStatus.CONFLICT, UNAVAILABLE_TITLE,
                    description == null || description.isBlank() ? UNREADABLE_KEY_MESSAGE : description,
                            AiMode.API_KEY);
            case PERMISSION_DENIED -> new AssistantRejectedException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Chave de IA recusada", REJECTED_KEY_MESSAGE, null);
            case RESOURCE_EXHAUSTED -> new AssistantRejectedException(HttpStatus.TOO_MANY_REQUESTS,
                    "Limite do provedor de IA", RATE_LIMIT_MESSAGE, null);
            case DEADLINE_EXCEEDED -> new AssistantRejectedException(HttpStatus.GATEWAY_TIMEOUT, "Prazo esgotado",
                    "A resposta passou do prazo de " + timeoutSeconds + " segundos. Tente de novo ou faça uma pergunta"
                            + " mais específica.", null);
            default -> {
                log.warn("Pergunta ao assistente: rag respondeu {} ({})", status.getCode(), description);
                yield new AssistantRejectedException(HttpStatus.SERVICE_UNAVAILABLE, "Assistente indisponível",
                        UNAVAILABLE_MESSAGE, null);
            }
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
