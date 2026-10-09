package br.com.condominioauditoria.api.service.assistant;

import br.com.condominioauditoria.api.dto.request.assistant.DocumentSearchRequest;
import br.com.condominioauditoria.api.dto.response.assistant.DocumentChunkResponse;
import br.com.condominioauditoria.api.exception.AssistantRejectedException;
import br.com.condominioauditoria.api.exception.InvalidRequestException;
import br.com.condominioauditoria.api.grpc.client.AssistantClient;
import br.com.condominioauditoria.api.mapper.AssistantMapper;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.feature.FeatureService;
import br.com.condominioauditoria.api.service.usage.UsageService;
import br.com.condominioauditoria.contratos.assistente.v1.BuscarRequest;
import br.com.condominioauditoria.contratos.assistente.v1.BuscarResponse;
import br.com.condominioauditoria.contratos.assistente.v1.FiltrosBusca;
import br.com.condominioauditoria.contratos.assistente.v1.ModoBusca;
import br.com.condominioauditoria.contratos.assistente.v1.Trecho;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Keyword search in the documents from the screen (RF-04.18): no AI, in any AI mode (including DESLIGADO, Q7), only
 * with the Assistant feature enabled. Always the rag's PALAVRA mode. Same second barrier as the chat and usage record
 * "busca_documentos" (without the searched text).
 */
@Service
public class AssistantSearchService {

    private static final Logger log = LoggerFactory.getLogger(AssistantSearchService.class);
    static final int TEXT_MAX_LENGTH = 500;
    static final int DEFAULT_LIMIT = 10;
    static final int MAX_LIMIT = 50;
    static final String UNAVAILABLE_MESSAGE = "Busca nos documentos indisponível no momento: o serviço rag não"
            + " respondeu. Tente de novo em instantes.";

    private final CondominiumAccess access;
    private final FeatureService features;
    private final AssistantClient rag;
    private final FileAccessBarrier barrier;
    private final UsageService usage;

    public AssistantSearchService(CondominiumAccess access, FeatureService features, AssistantClient rag,
            FileAccessBarrier barrier,
            UsageService usage) {
        this.access = access;
        this.features = features;
        this.rag = rag;
        this.barrier = barrier;
        this.usage = usage;
    }

    public List<DocumentChunkResponse> search(UUID condominiumId, DocumentSearchRequest request) {
        features.require(condominiumId, FeatureService.ASSISTANT);
        BuscarRequest ragRequest = buildRequest(condominiumId, request);
        String authorization = access.bearerToken().orElseThrow(() -> new IllegalStateException("Token ausente"));
        BuscarResponse response;
        try {
            response = rag.search(ragRequest, authorization);
        } catch (StatusRuntimeException error) {
            throw translate(error);
        }
        Set<String> visible = barrier.visibleIds(condominiumId, response.getTrechosList());
        List<Trecho> allowed = response.getTrechosList().stream()
                .filter(t -> FileAccessBarrier.isAllowed(t, visible)).toList();
        if (allowed.size() < response.getTrechosCount()) {
            log.warn("Busca nos documentos: {} trecho(s) do rag descartado(s) pela segunda barreira (condomínio {})",
                    response.getTrechosCount() - allowed.size(), condominiumId);
        }
        usage.recordDocumentSearch(condominiumId, access.username());
        return allowed.stream().map(AssistantMapper::toResponse).toList();
    }

    static BuscarRequest buildRequest(UUID condominiumId, DocumentSearchRequest request) {
        String text = request == null || request.text() == null ? "" : request.text().strip();
        if (text.isEmpty()) {
            throw new InvalidRequestException("Informe o texto da busca");
        }
        if (text.length() > TEXT_MAX_LENGTH) {
            throw new InvalidRequestException("O texto da busca passa de " + TEXT_MAX_LENGTH + " caracteres");
        }
        int limit = request.limit() == null ? DEFAULT_LIMIT : request.limit();
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidRequestException("O limite deve ser de 1 a " + MAX_LIMIT);
        }
        var builder = BuscarRequest.newBuilder()
                .setCondominioId(condominiumId.toString())
                .setTexto(text)
                .setModo(ModoBusca.MODO_BUSCA_PALAVRA)
                .setLimite(limit);
        FiltrosBusca filters = RagRequests.filters(request.filters());
        if (filters != null) {
            builder.setFiltros(filters);
        }
        return builder.build();
    }

    private static RuntimeException translate(StatusRuntimeException error) {
        Status status = error.getStatus();
        if (status.getCode() == Status.Code.INVALID_ARGUMENT) {
            return new InvalidRequestException(Objects.requireNonNullElse(status.getDescription(),
                    "Pedido de busca inválido"));
        }
        log.warn("Busca nos documentos: rag respondeu {} ({})", status.getCode(), status.getDescription());
        return new AssistantRejectedException(HttpStatus.SERVICE_UNAVAILABLE, "Busca indisponível", UNAVAILABLE_MESSAGE,
                null);
    }
}
