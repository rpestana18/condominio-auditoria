package br.com.condominioauditoria.rag.grpc;

import br.com.condominioauditoria.contracts.assistant.v2.Progress;
import br.com.condominioauditoria.contracts.assistant.v2.AssistantGrpc;
import br.com.condominioauditoria.contracts.assistant.v2.SearchRequest;
import br.com.condominioauditoria.contracts.assistant.v2.SearchResponse;
import br.com.condominioauditoria.contracts.assistant.v2.StoredData;
import br.com.condominioauditoria.contracts.assistant.v2.AskStage;
import br.com.condominioauditoria.contracts.assistant.v2.SearchFilters;
import br.com.condominioauditoria.contracts.assistant.v2.DataRow;
import br.com.condominioauditoria.contracts.assistant.v2.ListProvidersRequest;
import br.com.condominioauditoria.contracts.assistant.v2.ListProvidersResponse;
import br.com.condominioauditoria.contracts.assistant.v2.PageLocation;
import br.com.condominioauditoria.contracts.assistant.v2.ParagraphsLocation;
import br.com.condominioauditoria.contracts.assistant.v2.SheetLocation;
import br.com.condominioauditoria.contracts.assistant.v2.ProviderModel;
import br.com.condominioauditoria.contracts.assistant.v2.SearchMode;
import br.com.condominioauditoria.contracts.assistant.v2.DocumentParagraph;
import br.com.condominioauditoria.contracts.assistant.v2.QueryParameter;
import br.com.condominioauditoria.contracts.assistant.v2.AskEvent;
import br.com.condominioauditoria.contracts.assistant.v2.AskRequest;
import br.com.condominioauditoria.contracts.assistant.v2.Provider;
import br.com.condominioauditoria.contracts.assistant.v2.Answer;
import br.com.condominioauditoria.contracts.assistant.v2.AnswerOutcome;
import br.com.condominioauditoria.contracts.assistant.v2.IndexedChunk;
import br.com.condominioauditoria.contracts.assistant.v2.AskUsage;
import br.com.condominioauditoria.contracts.assistant.v2.ProviderUsage;
import br.com.condominioauditoria.rag.client.ModelContract;
import br.com.condominioauditoria.rag.config.properties.AiProperties;
import br.com.condominioauditoria.rag.dto.QueriedData;
import br.com.condominioauditoria.rag.dto.QuestionRequest;
import br.com.condominioauditoria.rag.dto.QuestionResult;
import br.com.condominioauditoria.rag.repository.IndexRepository;
import br.com.condominioauditoria.rag.search.DocumentSearch;
import br.com.condominioauditoria.rag.search.EmbeddingGenerator;
import br.com.condominioauditoria.rag.search.FoundChunk;
import br.com.condominioauditoria.rag.search.Location;
import br.com.condominioauditoria.rag.security.KeyEnvelope;
import br.com.condominioauditoria.rag.security.RagKeys;
import br.com.condominioauditoria.rag.service.ProviderCatalog;
import br.com.condominioauditoria.rag.service.QuestionService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implementation of contracts/grpc/assistant/v2: Search (delivery 1), Ask and ListProviders (delivery 3).
 * Validates the input (INVALID_ARGUMENT with a readable reason), converts it to the index search and returns the
 * citable chunks in order of relevance; in Ask, sends the progress events and, last, the already checked answer.
 *
 * No error message carries the AI key or any part of it.
 */
@Component
public class AssistantGrpcService extends AssistantGrpc.AssistantImplBase {

    private static final Logger log = LoggerFactory.getLogger(AssistantGrpcService.class);

    private final DocumentSearch search;
    private final EmbeddingGenerator embeddings;
    private final QuestionService questions;
    private final ProviderCatalog catalog;
    private final RagKeys keys;

    public AssistantGrpcService(DocumentSearch search, EmbeddingGenerator embeddings, QuestionService questions,
            ProviderCatalog catalog, RagKeys keys) {
        this.search = search;
        this.embeddings = embeddings;
        this.questions = questions;
        this.catalog = catalog;
        this.keys = keys;
    }

    @Override
    public void search(SearchRequest request, StreamObserver<SearchResponse> response) {
        IndexRepository.SearchFilters filters;
        DocumentSearch.Mode mode;
        try {
            if (request.getText().isBlank()) {
                throw new IllegalArgumentException("Texto da busca é obrigatório");
            }
            if (request.getLimit() < 0) {
                throw new IllegalArgumentException("Limite não pode ser negativo");
            }
            if (!embeddings.accepts(request.getEmbeddingModel())) {
                throw new IllegalArgumentException("Modelo de embeddings " + request.getEmbeddingModel()
                        + " não está disponível neste rag (disponível: " + embeddings.model() + ")");
            }
            filters = filters(request);
            mode = request.getMode() == SearchMode.SEARCH_MODE_KEYWORD ? DocumentSearch.Mode.KEYWORD
                    : DocumentSearch.Mode.HYBRID;
        } catch (IllegalArgumentException error) {
            response.onError(Status.INVALID_ARGUMENT.withDescription(error.getMessage()).asRuntimeException());
            return;
        }
        try {
            DocumentSearch.Result result = search.search(filters, request.getText(), mode, request.getLimit());
            var output = SearchResponse.newBuilder()
                    .setModeUsed(result.modeUsed() == DocumentSearch.Mode.KEYWORD ? SearchMode.SEARCH_MODE_KEYWORD
                            : SearchMode.SEARCH_MODE_HYBRID);
            result.chunks().forEach(t -> output.addChunks(chunk(t)));
            response.onNext(output.build());
            response.onCompleted();
        } catch (RuntimeException error) {
            log.warn("Falha na busca do condomínio {}: {}", request.getCondominiumId(), error.getMessage(), error);
            response.onError(Status.INTERNAL.withDescription("Falha na busca nos documentos").asRuntimeException());
        }
    }

    // -----------------------------------------------------------------------------------------------------------
    // Ask (delivery 3)
    // -----------------------------------------------------------------------------------------------------------

    @Override
    public void ask(AskRequest request, StreamObserver<AskEvent> stream) {
        String authorization = GrpcAuthorization.AUTHORIZATION.get();
        if (authorization == null || authorization.isBlank()) {
            stream.onError(Status.UNAUTHENTICATED
                    .withDescription("Chamada sem token: o metadado authorization é obrigatório.")
                    .asRuntimeException());
            return;
        }
        QuestionRequest input;
        try {
            input = toRequest(request, authorization);
        } catch (IllegalArgumentException error) {
            stream.onError(Status.INVALID_ARGUMENT.withDescription(error.getMessage()).asRuntimeException());
            return;
        }
        try {
            QuestionResult result = questions.answer(input, (stage, attempt) -> stream
                    .onNext(AskEvent.newBuilder().setProgress(Progress.newBuilder()
                            .setStage(stage(stage)).setAttempt(attempt)).build()));
            stream.onNext(AskEvent.newBuilder().setAnswer(response(result)).build());
            stream.onCompleted();
        } catch (QuestionService.InvalidQuestionException error) {
            stream.onError(Status.INVALID_ARGUMENT.withDescription(error.getMessage()).asRuntimeException());
        } catch (QuestionService.MissingConfigurationException | ProviderCatalog.ModelNotInCatalogException
                | RagKeys.NoKeyPairException error) {
            stream.onError(Status.FAILED_PRECONDITION.withDescription(error.getMessage()).asRuntimeException());
        } catch (KeyEnvelope.UnreadableKeyException error) {
            log.warn("Chave de IA do condomínio {} não pôde ser lida: {}", request.getCondominiumId(),
                    error.getMessage());
            stream.onError(Status.FAILED_PRECONDITION.withDescription(
                    "A chave de IA deste condomínio não pôde ser lida; cadastre de novo.").asRuntimeException());
        } catch (ModelContract.ProviderErrorException error) {
            stream.onError(providerError(error));
        } catch (StatusRuntimeException error) {
            // Comes from the numeric tools in the api (Consulta gRPC)
            log.warn("Ferramenta do assistente falhou: {}", error.getStatus());
            stream.onError(Status.UNAVAILABLE.withDescription(
                    "Não foi possível consultar os dados gravados agora; tente de novo em instantes.")
                    .asRuntimeException());
        } catch (RuntimeException error) {
            log.error("Falha ao responder a pergunta do condomínio {}: {}", request.getCondominiumId(),
                    error.getMessage(), error);
            stream.onError(Status.INTERNAL.withDescription("Falha ao responder a pergunta.").asRuntimeException());
        }
    }

    private static StatusRuntimeException providerError(ModelContract.ProviderErrorException error) {
        return switch (error.kind()) {
            case KEY_REJECTED -> Status.PERMISSION_DENIED
                    .withDescription("A chave de API do condomínio foi recusada pelo provedor.").asRuntimeException();
            case RATE_LIMITED -> Status.RESOURCE_EXHAUSTED
                    .withDescription("O limite de uso da chave de IA deste condomínio foi atingido; tente mais tarde.")
                    .asRuntimeException();
            case UNAVAILABLE -> Status.UNAVAILABLE
                    .withDescription("O provedor de IA está indisponível agora; tente de novo em instantes.")
                    .asRuntimeException();
            case INVALID_REQUEST -> Status.INTERNAL
                    .withDescription("O provedor de IA recusou o pedido do assistente.").asRuntimeException();
        };
    }

    private QuestionRequest toRequest(AskRequest request, String authorization) {
        String question = request.getQuestion().strip();
        if (question.isEmpty()) {
            throw new IllegalArgumentException("A pergunta é obrigatória.");
        }
        if (question.length() > QuestionRequest.MAX_CHARS) {
            throw new IllegalArgumentException(
                    "A pergunta passa de " + QuestionRequest.MAX_CHARS + " caracteres.");
        }
        if (request.getChunkLimit() < 0) {
            throw new IllegalArgumentException("limite_trechos não pode ser negativo");
        }
        var configuration = request.getConfiguration();
        if (!embeddings.accepts(configuration.getEmbeddingModel())) {
            throw new IllegalArgumentException("Modelo de embeddings " + configuration.getEmbeddingModel()
                    + " não está disponível neste rag (disponível: " + embeddings.model() + ")");
        }
        var filters = filters(request.getCondominiumId(), request.getFilters());
        var mode = configuration.getSearchMode() == SearchMode.SEARCH_MODE_KEYWORD ? DocumentSearch.Mode.KEYWORD
                : DocumentSearch.Mode.HYBRID;
        var history = request.getHistoryList().stream()
                .map(t -> new QuestionRequest.Exchange(t.getQuestion(), t.getAnswer())).toList();
        return new QuestionRequest(filters, question, history, mode, configuration.getProvider(),
                configuration.getModel(), configuration.getEncryptedKey().toByteArray(), request.getChunkLimit(),
                authorization);
    }

    private static AskStage stage(QuestionService.Stage stage) {
        return switch (stage) {
            case SEARCHING_CHUNKS -> AskStage.ASK_STAGE_SEARCHING_CHUNKS;
            case QUERYING_DATA -> AskStage.ASK_STAGE_QUERYING_DATA;
            case DRAFTING -> AskStage.ASK_STAGE_DRAFTING;
            case VALIDATING -> AskStage.ASK_STAGE_VALIDATING;
            case RETRYING -> AskStage.ASK_STAGE_RETRYING;
        };
    }

    private static Answer response(QuestionResult result) {
        var output = Answer.newBuilder()
                .setOutcome(result.status() == QuestionResult.Status.ANSWERED
                        ? AnswerOutcome.ANSWER_OUTCOME_ANSWERED
                        : AnswerOutcome.ANSWER_OUTCOME_NOT_FOUND)
                .setSuggestion(result.suggestion() == null ? "" : result.suggestion())
                .setWarning(result.warning() == null ? "" : result.warning())
                .setUsage(usage(result.usage()));
        result.inDocuments().forEach(p -> output.addInDocuments(DocumentParagraph.newBuilder()
                .setText(p.text()).addAllChunkIds(p.chunkIds())));
        result.inStoredData().forEach(d -> output.addInStoredData(storedData(d)));
        result.citedChunks().forEach(t -> output.addCitedChunks(chunk(t)));
        return output.build();
    }

    private static StoredData storedData(QuestionResult.DataItem dataItem) {
        QueriedData queried = dataItem.queried();
        var output = StoredData.newBuilder()
                .setCallId(queried.callId())
                .setQuery(queried.query())
                .setComment(dataItem.comment() == null ? "" : dataItem.comment());
        queried.params().forEach(p -> output.addParameters(QueryParameter.newBuilder()
                .setName(p.name()).setValue(p.value())));
        queried.rows().forEach(l -> output.addRows(DataRow.newBuilder()
                .setLabel(l.label()).setValue(l.value())));
        return output.build();
    }

    private static AskUsage usage(QuestionResult.Usage usage) {
        return AskUsage.newBuilder()
                .setInputTokens(usage.inputTokens())
                .setOutputTokens(usage.outputTokens())
                .setProvider(usage.provider())
                .setModel(usage.model())
                .setPromptVersion(usage.promptVersion())
                .setAttempts(usage.attempts())
                .build();
    }

    // -----------------------------------------------------------------------------------------------------------
    // ListProviders (delivery 3)
    // -----------------------------------------------------------------------------------------------------------

    @Override
    public void listProviders(ListProvidersRequest request, StreamObserver<ListProvidersResponse> response) {
        String authorization = GrpcAuthorization.AUTHORIZATION.get();
        if (authorization == null || authorization.isBlank()) {
            response.onError(Status.UNAUTHENTICATED
                    .withDescription("Chamada sem token: o metadado authorization é obrigatório.")
                    .asRuntimeException());
            return;
        }
        var output = ListProvidersResponse.newBuilder().setPublicKeyPem(keys.publicPem());
        catalog.providers().forEach(p -> output.addProviders(provider(p)));
        response.onNext(output.build());
        response.onCompleted();
    }

    private static Provider provider(AiProperties.AiProvider provider) {
        var output = Provider.newBuilder()
                .setCode(provider.code())
                .setName(provider.name())
                .setType(provider.type())
                .setUsage(provider.function() == AiProperties.AiFunction.ANSWERS ? ProviderUsage.PROVIDER_USAGE_ANSWERS
                        : ProviderUsage.PROVIDER_USAGE_EMBEDDINGS)
                .setLocal(provider.local())
                .setRequiresKey(provider.requiresKey())
                .setDimension(provider.dimension());
        provider.models().forEach(m -> output.addModels(ProviderModel.newBuilder()
                .setId(m.id())
                .setName(m.name())
                .setIsDefault(m.isDefault())
                .setInputPricePerMillionUsd(m.inputPricePerMillionUsd())
                .setOutputPricePerMillionUsd(m.outputPricePerMillionUsd())));
        return output.build();
    }

    private static IndexRepository.SearchFilters filters(SearchRequest request) {
        return filters(request.getCondominiumId(), request.getFilters());
    }

    private static IndexRepository.SearchFilters filters(String condominiumId, SearchFilters f) {
        UUID condominium = uuid(condominiumId, "condominio_id");
        LocalDate start = date(f.getDateFrom(), "data_inicio");
        LocalDate end = date(f.getDateTo(), "data_fim");
        if (start != null && end != null && start.isAfter(end)) {
            throw new IllegalArgumentException("data_inicio depois de data_fim");
        }
        List<UUID> files = f.getFileIdsList().stream().map(id -> uuid(id, "arquivo_ids")).toList();
        List<String> categories = f.getCategoriesList().stream().filter(c -> !c.isBlank()).toList();
        return new IndexRepository.SearchFilters(condominium, categories, start, end, files);
    }

    private static UUID uuid(String value, String field) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(field + " inválido: \"" + value + "\"");
        }
    }

    private static LocalDate date(String value, String field) {
        if (value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException(field + " fora do formato AAAA-MM-DD: \"" + value + "\"");
        }
    }

    public static IndexedChunk chunk(FoundChunk t) {
        var local = br.com.condominioauditoria.contracts.assistant.v2.ChunkLocation.newBuilder();
        switch (t.location()) {
            case Location.Page l -> local.setPage(PageLocation.newBuilder().setPage(l.number()));
            case Location.Sheet l -> local.setSheet(SheetLocation.newBuilder().setTab(l.tab())
                    .setStartRow(l.startRow()).setEndRow(l.endRow()));
            case Location.Paragraphs l -> local.setParagraphs(ParagraphsLocation.newBuilder()
                    .setParagraphStart(l.start()).setParagraphEnd(l.end())
                    .setSection(l.section() == null ? "" : l.section()));
        }
        return IndexedChunk.newBuilder()
                .setChunkId(t.chunkId().toString())
                .setFileId(t.fileId().toString())
                .setFileName(t.fileName())
                .setCategory(t.category())
                .setLocation(local)
                .setText(t.text())
                .setScore(t.score())
                .setSha256(t.sha256())
                .build();
    }
}
