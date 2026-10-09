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
import br.com.condominioauditoria.contratos.assistente.v1.BuscarRequest;
import br.com.condominioauditoria.contratos.assistente.v1.BuscarResponse;
import br.com.condominioauditoria.contratos.assistente.v1.FiltrosBusca;
import br.com.condominioauditoria.contratos.assistente.v1.Localizacao;
import br.com.condominioauditoria.contratos.assistente.v1.ModoBusca;
import br.com.condominioauditoria.contratos.assistente.v1.Trecho;
import br.com.condominioauditoria.contratos.consulta.v1.BuscarDocumentosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.BuscarDocumentosResponse;
import br.com.condominioauditoria.contratos.consulta.v1.FiltrosDocumentos;
import br.com.condominioauditoria.contratos.consulta.v1.LocalPagina;
import br.com.condominioauditoria.contratos.consulta.v1.LocalParagrafos;
import br.com.condominioauditoria.contratos.consulta.v1.LocalPlanilha;
import br.com.condominioauditoria.contratos.consulta.v1.LocalizacaoTrecho;
import br.com.condominioauditoria.contratos.consulta.v1.ModoBuscaDocumentos;
import br.com.condominioauditoria.contratos.consulta.v1.TrechoDocumento;
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
 * rpc BuscarDocumentos (contracts/grpc/consulta/v1, ADR 0003, Decision 5.3): validates the request, forwards it to the
 * rag (Assistente.Buscar) with the user's token and, on the way back, drops chunks of files that do not exist in the
 * api for the requested condominium (second barrier, besides the condominium filter the rag already applies).
 *
 * Belongs to the Assistant feature (RF-10.3): with it disabled in the condominium, rejects with FAILED_PRECONDITION
 * "Módulo Assistente não contratado para este condomínio." without calling the rag. Each answered search creates a
 * "chamada_mcp" usage record (RF-09.7; this rpc's caller is the mcp).
 *
 * Search mode by the condominium's AI configuration (delivery 3, Q16): embeddings DESLIGADO = PALAVRA; LOCAL = HIBRIDA
 * with the configured model (the rag falls back to PALAVRA if embeddings are down). Works in any answers mode,
 * including DESLIGADO.
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
    public BuscarDocumentosResponse search(UUID condominiumId, BuscarDocumentosRequest request) {
        features.require(condominiumId, FeatureService.ASSISTANT);
        var embeddings = aiConfiguration.read(condominiumId).embeddings();
        BuscarRequest ragRequest = toRagRequest(condominiumId, request).toBuilder()
                .setModo(embeddings.mode() == AiMode.DESLIGADO ? ModoBusca.MODO_BUSCA_PALAVRA
                        : ModoBusca.MODO_BUSCA_HIBRIDA)
                .setModeloEmbeddings(embeddings.mode() == AiMode.LOCAL && embeddings.model() != null
                        ? embeddings.model() : "")
                .build();
        String authorization = access.bearerToken()
                .orElseThrow(() -> Status.UNAUTHENTICATED.withDescription("Token ausente").asRuntimeException());
        BuscarResponse response;
        try {
            response = rag.search(ragRequest, authorization);
        } catch (StatusRuntimeException error) {
            throw translateRagError(error);
        }
        usage.recordMcpCall(condominiumId, access.username(), response.getModoUsado() == ModoBusca.MODO_BUSCA_HIBRIDA);
        return BuscarDocumentosResponse.newBuilder()
                .addAllTrechos(allowed(condominiumId, response.getTrechosList()).stream()
                        .map(DocumentSearchService::convert).toList())
                .setModoUsado(switch (response.getModoUsado()) {
                    case MODO_BUSCA_PALAVRA -> ModoBuscaDocumentos.MODO_BUSCA_DOCUMENTOS_PALAVRA;
                    case MODO_BUSCA_HIBRIDA -> ModoBuscaDocumentos.MODO_BUSCA_DOCUMENTOS_HIBRIDA;
                    default -> ModoBuscaDocumentos.MODO_BUSCA_DOCUMENTOS_NAO_INFORMADO;
                })
                .build();
    }

    /** Contract validations: text required, non-negative limit (0 = 10, above 50 = 50), ISO dates. */
    static BuscarRequest toRagRequest(UUID condominiumId, BuscarDocumentosRequest request) {
        String text = request.getTexto().strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Informe o texto da busca");
        }
        if (request.getLimite() < 0) {
            throw new IllegalArgumentException("Limite não pode ser negativo: " + request.getLimite());
        }
        int limit = request.getLimite() == 0 ? DEFAULT_LIMIT : Math.min(request.getLimite(), MAX_LIMIT);
        var builder = BuscarRequest.newBuilder()
                .setCondominioId(condominiumId.toString())
                .setTexto(text)
                .setModo(ModoBusca.MODO_BUSCA_HIBRIDA)
                .setLimite(limit);
        if (request.hasFiltros()) {
            builder.setFiltros(filters(request.getFiltros()));
        }
        return builder.build();
    }

    private static FiltrosBusca filters(FiltrosDocumentos f) {
        String start = date(f.getDataInicio());
        String end = date(f.getDataFim());
        if (!start.isEmpty() && !end.isEmpty() && start.compareTo(end) > 0) {
            throw new IllegalArgumentException("Data inicial depois da final: " + start + " a " + end);
        }
        return FiltrosBusca.newBuilder()
                .addAllCategorias(f.getCategoriasList().stream().map(DocumentSearchService::category).distinct().toList())
                .setDataInicio(start)
                .setDataFim(end)
                .addAllArquivoIds(f.getArquivoIdsList().stream().map(DocumentSearchService::fileId).distinct().toList())
                .build();
    }

    /** Second barrier: only chunks of files that exist in the api and belong to the requested condominium remain. */
    private List<Trecho> allowed(UUID condominiumId, List<Trecho> chunks) {
        Set<UUID> cited = new HashSet<>();
        for (Trecho t : chunks) {
            optionalUuid(t.getArquivoId()).ifPresent(cited::add);
        }
        Set<String> ofCondominium = cited.isEmpty() ? Set.of()
                : files.findByCondominiumIdAndIdIn(condominiumId, cited).stream()
                        .map(SourceFile::getId).map(UUID::toString).collect(Collectors.toSet());
        List<Trecho> list = chunks.stream()
                .filter(t -> optionalUuid(t.getArquivoId()).map(UUID::toString).filter(ofCondominium::contains).isPresent())
                .toList();
        if (list.size() < chunks.size()) {
            log.warn("Busca nos documentos: {} trecho(s) do rag descartado(s) por arquivo fora do condomínio {}",
                    chunks.size() - list.size(), condominiumId);
        }
        return list;
    }

    static TrechoDocumento convert(Trecho t) {
        return TrechoDocumento.newBuilder()
                .setTrechoId(t.getTrechoId())
                .setArquivoId(t.getArquivoId())
                .setNomeArquivo(t.getNomeArquivo())
                .setCategoria(t.getCategoria())
                .setLocalizacao(location(t.getLocalizacao()))
                .setTexto(t.getTexto())
                .setPontuacao(t.getPontuacao())
                .setSha256(t.getSha256())
                .build();
    }

    private static LocalizacaoTrecho location(Localizacao l) {
        var builder = LocalizacaoTrecho.newBuilder();
        switch (l.getTipoCase()) {
            case PAGINA -> builder.setPagina(LocalPagina.newBuilder().setPagina(l.getPagina().getPagina()));
            case PLANILHA -> builder.setPlanilha(LocalPlanilha.newBuilder()
                    .setAba(l.getPlanilha().getAba())
                    .setLinhaInicio(l.getPlanilha().getLinhaInicio())
                    .setLinhaFim(l.getPlanilha().getLinhaFim()));
            case PARAGRAFOS -> builder.setParagrafos(LocalParagrafos.newBuilder()
                    .setParagrafoInicio(l.getParagrafos().getParagrafoInicio())
                    .setParagrafoFim(l.getParagrafos().getParagrafoFim())
                    .setSecao(l.getParagrafos().getSecao()));
            case TIPO_NOT_SET -> {
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
