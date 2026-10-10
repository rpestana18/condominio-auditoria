package br.com.condominioauditoria.rag.service;

import br.com.condominioauditoria.rag.client.AiGateway;
import br.com.condominioauditoria.rag.client.ModelContract.Parameters;
import br.com.condominioauditoria.rag.client.ModelContract.Stop;
import br.com.condominioauditoria.rag.client.ModelContract.ToolCall;
import br.com.condominioauditoria.rag.client.ModelContract.ToolResult;
import br.com.condominioauditoria.rag.client.ModelContract.Turn;
import br.com.condominioauditoria.rag.client.QueryClient;
import br.com.condominioauditoria.rag.config.properties.AiProperties.AiModel;
import br.com.condominioauditoria.rag.config.properties.AiProperties.AiProvider;
import br.com.condominioauditoria.rag.config.properties.RagProperties;
import br.com.condominioauditoria.rag.dto.QueriedData;
import br.com.condominioauditoria.rag.dto.QuestionRequest;
import br.com.condominioauditoria.rag.dto.QuestionResult;
import br.com.condominioauditoria.rag.search.DocumentSearch;
import br.com.condominioauditoria.rag.search.FoundChunk;
import br.com.condominioauditoria.rag.security.RagKeys;
import br.com.condominioauditoria.rag.service.ResponseSchema.DataComment;
import br.com.condominioauditoria.rag.service.ResponseSchema.ModelParagraph;
import br.com.condominioauditoria.rag.service.ResponseSchema.ModelResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The assistant chat end to end (RF-04.8 to 04.16, ADR 0003, Decisions 1 and 5.2):
 * <ol>
 * <li>hybrid (or keyword) search filtered over the condominium's indexed documents;</li>
 * <li>a conversation with the model through the ai-gateway, with the chunks and the four numeric tools; the tool loop
 * is done here, with a round ceiling, and each tool runs in the api with the user's token;</li>
 * <li>check of the answer ({@link ResponseValidator}); rejected, one retry with the reason; rejected again, "Não
 * encontrei nos documentos.";</li>
 * <li>the "Nos dados gravados" block is built from the tools' result, never from the model's text.</li>
 * </ol>
 * No condominium configuration is read from a database; nothing of the API key goes to the log.
 */
@Service
public class QuestionService {

    private static final Logger log = LoggerFactory.getLogger(QuestionService.class);

    /** One retry, as the specification requires (RF-04.12). */
    private static final int ATTEMPTS = 2;

    private final DocumentSearch search;
    private final ProviderCatalog catalog;
    private final RagKeys keys;
    private final AiGateway gateway;
    private final NumericTools tools;
    private final QueryClient queryClient;
    private final ResponseValidator validator;
    private final RagProperties.Assistant config;

    public QuestionService(DocumentSearch search, ProviderCatalog catalog, RagKeys keys, AiGateway gateway,
            NumericTools tools, QueryClient queryClient, ResponseValidator validator,
            RagProperties properties) {
        this.search = search;
        this.catalog = catalog;
        this.keys = keys;
        this.gateway = gateway;
        this.tools = tools;
        this.queryClient = queryClient;
        this.validator = validator;
        this.config = properties.assistant();
    }

    /** Stages of the stream's progress event (assistant.proto, AskStage). */
    public enum Stage {
        SEARCHING_CHUNKS, QUERYING_DATA, DRAFTING, VALIDATING, RETRYING
    }

    /** Who receives the progress events (the gRPC stream). */
    public interface ProgressListener {
        void stage(Stage stage, int attempt);
    }

    /** Empty or too long question, or filter out of format: INVALID_ARGUMENT. */
    public static class InvalidQuestionException extends RuntimeException {
        public InvalidQuestionException(String message) {
            super(message);
        }
    }

    /** Missing configuration to answer (missing key, provider not in the catalog): FAILED_PRECONDITION. */
    public static class MissingConfigurationException extends RuntimeException {
        public MissingConfigurationException(String message) {
            super(message);
        }
    }

    public QuestionResult answer(QuestionRequest request, ProgressListener progress) {
        String question = request.question() == null ? "" : request.question().strip();
        if (question.isEmpty()) {
            throw new InvalidQuestionException("a pergunta é obrigatória");
        }
        if (question.length() > QuestionRequest.MAX_CHARS) {
            throw new InvalidQuestionException(
                    "a pergunta passa de " + QuestionRequest.MAX_CHARS + " caracteres");
        }
        if (request.encryptedKey() == null || request.encryptedKey().length == 0) {
            throw new MissingConfigurationException("este condomínio não tem chave de IA cadastrada");
        }
        AiProvider provider = catalog.byCode(request.provider())
                .orElseThrow(() -> new MissingConfigurationException(
                        "provedor de IA \"" + request.provider() + "\" não está no catálogo deste rag"));
        AiModel model = catalog.answerModel(request.provider(), request.model());
        if (!keys.hasKeyPair()) {
            throw new MissingConfigurationException(
                    "este rag está sem par de chaves: não é possível ler a chave de IA do condomínio");
        }
        String apiKey = keys.openApiKey(request.encryptedKey());

        progress.stage(Stage.SEARCHING_CHUNKS, 1);
        int limit = request.chunkLimit() <= 0 ? QuestionRequest.DEFAULT_CHUNKS
                : Math.min(request.chunkLimit(), QuestionRequest.MAX_CHUNKS);
        DocumentSearch.Result found = search.search(request.filters(), question, request.requestedMode(), limit);
        Map<String, FoundChunk> byId = new LinkedHashMap<>();
        found.chunks().forEach(t -> byId.put(t.chunkId().toString(), t));
        String warning = request.requestedMode() == DocumentSearch.Mode.HYBRID
                && found.modeUsed() == DocumentSearch.Mode.KEYWORD
                        ? "A busca por significado está indisponível agora; a resposta usou só a busca por palavra."
                        : "";

        try (AiGateway.Conversation conversation = gateway.open(parameters(provider, model, apiKey))) {
            List<QuestionRequest.Exchange> history = request.history() == null ? List.of() : request.history();
            history.stream().skip(Math.max(0, history.size() - config.maxHistory()))
                    .forEach(t -> conversation.addPreviousExchange(t.question(), t.answer()));
            conversation.addQuestion(AssistantInstructions.question(question, found.chunks()));
            return converse(request, conversation, byId, warning, progress);
        }
    }

    private QuestionResult converse(QuestionRequest request, AiGateway.Conversation conversation,
            Map<String, FoundChunk> byId, String warning, ProgressListener progress) {
        Map<String, QueriedData> calls = new LinkedHashMap<>();
        String lastReason = "";
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            Draft draft = draft(request, conversation, calls, attempt, progress);
            if (draft.safetyRefusal()) {
                // Model safety refusal: no retry (assistant.proto)
                return notFound(conversation, request, attempt, join(warning,
                        "O modelo recusou responder a esta pergunta por política de segurança do provedor."));
            }
            if (draft.reason() != null) {
                lastReason = draft.reason();
            } else {
                progress.stage(Stage.VALIDATING, attempt);
                Optional<String> error = validator.validate(draft.response(), byId, calls.keySet());
                if (error.isEmpty()) {
                    return build(draft.response(), byId, calls, conversation, request, attempt, warning);
                }
                lastReason = error.get();
            }
            log.info("Resposta do assistente reprovada na tentativa {}: {}", attempt, lastReason);
            if (attempt < ATTEMPTS) {
                progress.stage(Stage.RETRYING, attempt + 1);
                conversation.addQuestion(AssistantInstructions.retry(lastReason));
            }
        }
        return notFound(conversation, request, ATTEMPTS, warning);
    }

    /** One attempt: the tool loop until the model delivers the JSON (or hits the round ceiling). */
    private Draft draft(QuestionRequest request, AiGateway.Conversation conversation,
            Map<String, QueriedData> calls, int attempt, ProgressListener progress) {
        for (int round = 1; round <= Math.max(1, config.toolRounds()); round++) {
            progress.stage(Stage.DRAFTING, attempt);
            Turn turn = conversation.send();
            if (turn.stop() == Stop.REFUSAL) {
                return Draft.refusal();
            }
            if (turn.stop() == Stop.TOOL_USE) {
                progress.stage(Stage.QUERYING_DATA, attempt);
                conversation.addResults(execute(request, turn.calls(), calls));
                continue;
            }
            if (turn.stop() == Stop.TRUNCATED) {
                return Draft.reason("a resposta passou do tamanho máximo; responda de forma mais curta");
            }
            try {
                return Draft.ready(ResponseSchema.read(turn.jsonText()));
            } catch (ResponseSchema.UnreadableResponseException error) {
                return Draft.reason(error.getMessage());
            }
        }
        return Draft.reason("o limite de consultas desta pergunta foi atingido; responda com o que já tem");
    }

    private List<ToolResult> execute(QuestionRequest request, List<ToolCall> requested,
            Map<String, QueriedData> calls) {
        var api = queryClient.withToken(request.authorization());
        List<ToolResult> results = new ArrayList<>();
        for (ToolCall call : requested) {
            String callId = "c" + (calls.size() + 1);
            try {
                QueriedData dataItem = tools.execute(callId, call.name(), call.arguments(),
                        request.filters().condominiumId().toString(), api);
                calls.put(callId, dataItem);
                results.add(new ToolResult(call.id(), NumericTools.forModel(dataItem), false));
            } catch (NumericTools.UnknownToolException error) {
                results.add(new ToolResult(call.id(), error.getMessage(), true));
            } catch (StatusRuntimeException error) {
                Status.Code code = error.getStatus().getCode();
                if (code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED
                        || code == Status.Code.UNIMPLEMENTED) {
                    // api down = UNAVAILABLE in Ask; there is no way to answer any number
                    throw error;
                }
                log.info("Ferramenta {} recusada pelo backend ({}): {}", call.name(), code,
                        error.getStatus().getDescription());
                results.add(new ToolResult(call.id(),
                        "a consulta não foi autorizada ou os parâmetros são inválidos: "
                                + Optional.ofNullable(error.getStatus().getDescription()).orElse(code.name()),
                        true));
            }
        }
        return results;
    }

    private QuestionResult build(ModelResponse response, Map<String, FoundChunk> byId,
            Map<String, QueriedData> calls, AiGateway.Conversation conversation, QuestionRequest request, int attempts,
            String warning) {
        if (response.notFound() || response.isEmpty()) {
            return notFound(conversation, request, attempts, warning, response.suggestion());
        }
        List<QuestionResult.Paragraph> paragraphs = new ArrayList<>();
        var cited = new LinkedHashSet<String>();
        for (ModelParagraph p : response.inDocuments()) {
            paragraphs.add(new QuestionResult.Paragraph(p.text(), List.copyOf(p.chunkIds())));
            cited.addAll(p.chunkIds());
        }
        List<QuestionResult.DataItem> data = new ArrayList<>();
        for (DataComment d : response.inStoredData()) {
            data.add(new QuestionResult.DataItem(calls.get(d.callId()), d.comment()));
        }
        return new QuestionResult(QuestionResult.Status.ANSWERED, List.copyOf(paragraphs),
                List.copyOf(data), cited.stream().map(byId::get).toList(), response.suggestion(), warning,
                usage(conversation, request, attempts));
    }

    private QuestionResult notFound(AiGateway.Conversation conversation, QuestionRequest request, int attempts,
            String warning) {
        return notFound(conversation, request, attempts, warning, "");
    }

    private QuestionResult notFound(AiGateway.Conversation conversation, QuestionRequest request, int attempts,
            String warning, String suggestion) {
        return new QuestionResult(QuestionResult.Status.NOT_FOUND, List.of(), List.of(), List.of(),
                suggestion == null ? "" : suggestion, warning, usage(conversation, request, attempts));
    }

    private static String join(String first, String second) {
        return first == null || first.isBlank() ? second : first + " " + second;
    }

    private static QuestionResult.Usage usage(AiGateway.Conversation conversation, QuestionRequest request,
            int attempts) {
        return new QuestionResult.Usage(conversation.inputTokens(), conversation.outputTokens(), request.provider(),
                request.model(), AssistantInstructions.VERSION, attempts);
    }

    private Parameters parameters(AiProvider provider, AiModel model, String apiKey) {
        return new Parameters(provider.type(), model.id(), apiKey, AssistantInstructions.system(),
                config.maxTokens(), effort(model.id()), ResponseSchema.schema(), tools.definitions());
    }

    /**
     * Haiku 4.5 rejects {@code effort} (provider error): for that model only the structured output format is sent.
     * On the models that accept it, the configured value applies ({@code RAG_ESFORCO}, default medium).
     */
    private String effort(String modelId) {
        return modelId.contains("haiku") ? "" : config.effort();
    }

    /** State of one attempt: ready, rejected with a reason, or model safety refusal. */
    private record Draft(ModelResponse response, String reason, boolean safetyRefusal) {

        static Draft ready(ModelResponse response) {
            return new Draft(response, null, false);
        }

        static Draft reason(String reason) {
            return new Draft(null, reason, false);
        }

        static Draft refusal() {
            return new Draft(null, null, true);
        }
    }
}
