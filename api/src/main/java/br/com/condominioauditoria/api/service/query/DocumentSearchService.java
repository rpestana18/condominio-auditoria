package br.com.condominioauditoria.api.service.query;

import br.com.condominioauditoria.api.grpc.client.AssistantClient;
import br.com.condominioauditoria.api.model.enums.AiMode;
import br.com.condominioauditoria.api.model.enums.FileCategory;
import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.repository.file.SourceFileRepository;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.ai.AiConfigurationService;
import br.com.condominioauditoria.api.service.feature.FeatureService;
import br.com.condominioauditoria.api.service.usage.UsageService;
import br.com.condominioauditoria.contracts.assistant.v2.SearchRequest;
import br.com.condominioauditoria.contracts.assistant.v2.SearchResponse;
import br.com.condominioauditoria.contracts.assistant.v2.SearchFilters;
import br.com.condominioauditoria.contracts.assistant.v2.ChunkLocation;
import br.com.condominioauditoria.contracts.assistant.v2.SearchMode;
import br.com.condominioauditoria.contracts.assistant.v2.IndexedChunk;
import br.com.condominioauditoria.contracts.query.v2.SearchDocumentsRequest;
import br.com.condominioauditoria.contracts.query.v2.SearchDocumentsResponse;
import br.com.condominioauditoria.contracts.query.v2.DocumentFilters;
import br.com.condominioauditoria.contracts.query.v2.PageLocation;
import br.com.condominioauditoria.contracts.query.v2.ParagraphsLocation;
import br.com.condominioauditoria.contracts.query.v2.SheetLocation;
import br.com.condominioauditoria.contracts.query.v2.DocumentChunkLocation;
import br.com.condominioauditoria.contracts.query.v2.DocumentSearchMode;
import br.com.condominioauditoria.contracts.query.v2.DocumentChunk;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * rpc SearchDocuments (contracts/grpc/query/v2, ADR 0003, Decision 5.3): validates the request, forwards it to the
 * rag (Assistant.Search) with the user's token and, on the way back, drops chunks of files that do not exist in the
 * api for the requested condominium (second barrier, besides the condominium filter the rag already applies).
 *
 * Belongs to the Assistant feature (RF-10.3): with it disabled in the condominium, rejects with FAILED_PRECONDITION
 * "Módulo Assistente não contratado para este condomínio." without calling the rag. Each answered search creates a
 * "mcp_call" usage record (RF-09.7; this rpc's caller is the mcp).
 *
 * Search mode by the condominium's AI configuration (delivery 3, Q16): embeddings OFF = PALAVRA; LOCAL = HIBRIDA
 * with the configured model (the rag falls back to PALAVRA if embeddings are down). Works in any answers mode,
 * including OFF.
 */
@Component
public class DocumentSearchService {

    private static final Logger log = LoggerFactory.getLogger(DocumentSearchService.class);
    static final int DEFAULT_LIMIT = 10;
    static final int MAX_LIMIT = 50;

    private final CondominiumAccess access;
    private final SourceFileRepository files;
    private final AssistantClient rag;
    private final FeatureService features;
    private final UsageService usage;
    private final AiConfigurationService aiConfiguration;

    public DocumentSearchService(CondominiumAccess access, SourceFileRepository files, AssistantClient rag,
            FeatureService features,
            UsageService usage, AiConfigurationService aiConfiguration) {
        this.access = access;
        this.files = files;
        this.rag = rag;
        this.features = features;
        this.usage = usage;
        this.aiConfiguration = aiConfiguration;
    }

    /**
     * Called inside the rpc, with the token's user already in the security context and the condominium access checked.
     */
    public SearchDocumentsResponse search(UUID condominiumId, SearchDocumentsRequest request) {
        features.require(condominiumId, FeatureService.ASSISTANT);
        var embeddings = aiConfiguration.read(condominiumId).embeddings();
        SearchRequest ragRequest = toRagRequest(condominiumId, request).toBuilder()
                .setMode(embeddings.mode() == AiMode.OFF ? SearchMode.SEARCH_MODE_KEYWORD
                        : SearchMode.SEARCH_MODE_HYBRID)
                .setEmbeddingModel(embeddings.mode() == AiMode.LOCAL && embeddings.model() != null
                        ? embeddings.model() : "")
                .build();
        String authorization = access.bearerToken()
                .orElseThrow(() -> Status.UNAUTHENTICATED.withDescription("Token ausente").asRuntimeException());
        SearchResponse response;
        try {
            response = rag.search(ragRequest, authorization);
        } catch (StatusRuntimeException error) {
            throw translateRagError(error);
        }
        usage.recordMcpCall(condominiumId, access.username(), response.getModeUsed() == SearchMode.SEARCH_MODE_HYBRID);
        return SearchDocumentsResponse.newBuilder()
                .addAllChunks(allowed(condominiumId, response.getChunksList()).stream()
                        .map(DocumentSearchService::convert).toList())
                .setModeUsed(switch (response.getModeUsed()) {
                    case SEARCH_MODE_KEYWORD -> DocumentSearchMode.DOCUMENT_SEARCH_MODE_KEYWORD;
                    case SEARCH_MODE_HYBRID -> DocumentSearchMode.DOCUMENT_SEARCH_MODE_HYBRID;
                    default -> DocumentSearchMode.DOCUMENT_SEARCH_MODE_UNSPECIFIED;
                })
                .build();
    }

    /** Contract validations: text required, non-negative limit (0 = 10, above 50 = 50), ISO dates. */
    static SearchRequest toRagRequest(UUID condominiumId, SearchDocumentsRequest request) {
        String text = request.getText().strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Informe o texto da busca");
        }
        if (request.getLimit() < 0) {
            throw new IllegalArgumentException("Limite não pode ser negativo: " + request.getLimit());
        }
        int limit = request.getLimit() == 0 ? DEFAULT_LIMIT : Math.min(request.getLimit(), MAX_LIMIT);
        var builder = SearchRequest.newBuilder()
                .setCondominiumId(condominiumId.toString())
                .setText(text)
                .setMode(SearchMode.SEARCH_MODE_HYBRID)
                .setLimit(limit);
        if (request.hasFilters()) {
            builder.setFilters(filters(request.getFilters()));
        }
        return builder.build();
    }

    private static SearchFilters filters(DocumentFilters f) {
        String start = date(f.getDateFrom());
        String end = date(f.getDateTo());
        if (!start.isEmpty() && !end.isEmpty() && start.compareTo(end) > 0) {
            throw new IllegalArgumentException("Data inicial depois da final: " + start + " a " + end);
        }
        return SearchFilters.newBuilder()
                .addAllCategories(f.getCategoriesList().stream().map(DocumentSearchService::category).distinct().toList())
                .setDateFrom(start)
                .setDateTo(end)
                .addAllFileIds(f.getFileIdsList().stream().map(DocumentSearchService::fileId).distinct().toList())
                .build();
    }

    /** Second barrier: only chunks of files that exist in the api and belong to the requested condominium remain. */
    private List<IndexedChunk> allowed(UUID condominiumId, List<IndexedChunk> chunks) {
        Set<UUID> cited = new HashSet<>();
        for (IndexedChunk t : chunks) {
            optionalUuid(t.getFileId()).ifPresent(cited::add);
        }
        Set<String> ofCondominium = cited.isEmpty() ? Set.of()
                : files.findByCondominiumIdAndIdIn(condominiumId, cited).stream()
                        .map(SourceFile::getId).map(UUID::toString).collect(Collectors.toSet());
        List<IndexedChunk> list = chunks.stream()
                .filter(t -> optionalUuid(t.getFileId()).map(UUID::toString).filter(ofCondominium::contains).isPresent())
                .toList();
        if (list.size() < chunks.size()) {
            log.warn("Busca nos documentos: {} trecho(s) do rag descartado(s) por arquivo fora do condomínio {}",
                    chunks.size() - list.size(), condominiumId);
        }
        return list;
    }

    static DocumentChunk convert(IndexedChunk t) {
        return DocumentChunk.newBuilder()
                .setChunkId(t.getChunkId())
                .setFileId(t.getFileId())
                .setFileName(t.getFileName())
                .setCategory(t.getCategory())
                .setLocation(location(t.getLocation()))
                .setText(t.getText())
                .setScore(t.getScore())
                .setSha256(t.getSha256())
                .build();
    }

    private static DocumentChunkLocation location(ChunkLocation l) {
        var builder = DocumentChunkLocation.newBuilder();
        switch (l.getKindCase()) {
            case PAGE -> builder.setPage(PageLocation.newBuilder().setPage(l.getPage().getPage()));
            case SHEET -> builder.setSheet(SheetLocation.newBuilder()
                    .setTab(l.getSheet().getTab())
                    .setStartRow(l.getSheet().getStartRow())
                    .setEndRow(l.getSheet().getEndRow()));
            case PARAGRAPHS -> builder.setParagraphs(ParagraphsLocation.newBuilder()
                    .setParagraphStart(l.getParagraphs().getParagraphStart())
                    .setParagraphEnd(l.getParagraphs().getParagraphEnd())
                    .setSection(l.getParagraphs().getSection()));
            case KIND_NOT_SET -> {
            }
        }
        return builder.build();
    }

    /** rag error in Portuguese, with the code the query contract promises the mcp. */
    private static StatusRuntimeException translateRagError(StatusRuntimeException error) {
        Status status = error.getStatus();
        return switch (status.getCode()) {
            case INVALID_ARGUMENT -> Status.INVALID_ARGUMENT
                    .withDescription(Objects.requireNonNullElse(status.getDescription(), "Pedido de busca inválido"))
                    .asRuntimeException();
            case UNAVAILABLE, DEADLINE_EXCEEDED, UNIMPLEMENTED, CANCELLED -> {
                log.warn("Busca nos documentos: rag indisponível ({}: {})", status.getCode(), status.getDescription());
                yield Status.UNAVAILABLE
                        .withDescription("Busca nos documentos indisponível no momento: o serviço rag não respondeu."
                                + " Tente de novo em instantes.")
                        .asRuntimeException();
            }
            default -> {
                log.error("Busca nos documentos: erro no rag ({}: {})", status.getCode(), status.getDescription(),
                        error);
                yield Status.INTERNAL.withDescription("Erro no serviço de busca (rag)").asRuntimeException();
            }
        };
    }

    private static String category(String value) {
        try {
            return FileCategory.valueOf(value.strip().toUpperCase()).name();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Categoria desconhecida: " + value);
        }
    }

    private static String date(String value) {
        if (value.isBlank()) {
            return "";
        }
        try {
            return LocalDate.parse(value.strip()).toString();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Data inválida (use AAAA-MM-DD): " + value);
        }
    }

    private static String fileId(String value) {
        return optionalUuid(value)
                .orElseThrow(() -> new IllegalArgumentException("arquivo_id inválido: '" + value + "'"))
                .toString();
    }

    private static Optional<UUID> optionalUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value.strip()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
