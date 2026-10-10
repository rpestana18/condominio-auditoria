package br.com.condominioauditoria.rag.grpc;

import br.com.condominioauditoria.contratos.assistente.v1.Andamento;
import br.com.condominioauditoria.contratos.assistente.v1.AssistenteGrpc;
import br.com.condominioauditoria.contratos.assistente.v1.BuscarRequest;
import br.com.condominioauditoria.contratos.assistente.v1.BuscarResponse;
import br.com.condominioauditoria.contratos.assistente.v1.DadoGravado;
import br.com.condominioauditoria.contratos.assistente.v1.EtapaPergunta;
import br.com.condominioauditoria.contratos.assistente.v1.FiltrosBusca;
import br.com.condominioauditoria.contratos.assistente.v1.LinhaDado;
import br.com.condominioauditoria.contratos.assistente.v1.ListarProvedoresRequest;
import br.com.condominioauditoria.contratos.assistente.v1.ListarProvedoresResponse;
import br.com.condominioauditoria.contratos.assistente.v1.LocalPagina;
import br.com.condominioauditoria.contratos.assistente.v1.LocalParagrafos;
import br.com.condominioauditoria.contratos.assistente.v1.LocalPlanilha;
import br.com.condominioauditoria.contratos.assistente.v1.ModeloProvedor;
import br.com.condominioauditoria.contratos.assistente.v1.ModoBusca;
import br.com.condominioauditoria.contratos.assistente.v1.ParagrafoDocumentos;
import br.com.condominioauditoria.contratos.assistente.v1.ParametroConsulta;
import br.com.condominioauditoria.contratos.assistente.v1.PerguntarEvento;
import br.com.condominioauditoria.contratos.assistente.v1.PerguntarRequest;
import br.com.condominioauditoria.contratos.assistente.v1.Provedor;
import br.com.condominioauditoria.contratos.assistente.v1.RespostaPergunta;
import br.com.condominioauditoria.contratos.assistente.v1.SituacaoResposta;
import br.com.condominioauditoria.contratos.assistente.v1.Trecho;
import br.com.condominioauditoria.contratos.assistente.v1.UsoPergunta;
import br.com.condominioauditoria.contratos.assistente.v1.UsoProvedor;
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
 * Implementation of contracts/grpc/assistente/v1: Buscar (delivery 1), Perguntar and ListarProvedores (delivery 3).
 * Validates the input (INVALID_ARGUMENT with a readable reason), converts it to the index search and returns the
 * citable chunks in order of relevance; in Perguntar, sends the progress events and, last, the already checked answer.
 *
 * No error message carries the AI key or any part of it.
 */
@Component
public class AssistantGrpcService extends AssistenteGrpc.AssistenteImplBase {

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
    public void buscar(BuscarRequest request, StreamObserver<BuscarResponse> response) {
        IndexRepository.SearchFilters filters;
        DocumentSearch.Mode mode;
        try {
            if (request.getTexto().isBlank()) {
                throw new IllegalArgumentException("Texto da busca é obrigatório");
            }
            if (request.getLimite() < 0) {
                throw new IllegalArgumentException("Limite não pode ser negativo");
            }
            if (!embeddings.accepts(request.getModeloEmbeddings())) {
                throw new IllegalArgumentException("Modelo de embeddings " + request.getModeloEmbeddings()
                        + " não está disponível neste rag (disponível: " + embeddings.model() + ")");
            }
            filters = filters(request);
            mode = request.getModo() == ModoBusca.MODO_BUSCA_PALAVRA ? DocumentSearch.Mode.KEYWORD
                    : DocumentSearch.Mode.HYBRID;
        } catch (IllegalArgumentException error) {
            response.onError(Status.INVALID_ARGUMENT.withDescription(error.getMessage()).asRuntimeException());
            return;
        }
        try {
            DocumentSearch.Result result = search.search(filters, request.getTexto(), mode, request.getLimite());
            var output = BuscarResponse.newBuilder()
                    .setModoUsado(result.modeUsed() == DocumentSearch.Mode.KEYWORD ? ModoBusca.MODO_BUSCA_PALAVRA
                            : ModoBusca.MODO_BUSCA_HIBRIDA);
            result.chunks().forEach(t -> output.addTrechos(chunk(t)));
            response.onNext(output.build());
            response.onCompleted();
        } catch (RuntimeException error) {
            log.warn("Falha na busca do condomínio {}: {}", request.getCondominioId(), error.getMessage(), error);
            response.onError(Status.INTERNAL.withDescription("Falha na busca nos documentos").asRuntimeException());
        }
    }

    // -----------------------------------------------------------------------------------------------------------
    // Perguntar (delivery 3)
    // -----------------------------------------------------------------------------------------------------------

    @Override
    public void perguntar(PerguntarRequest request, StreamObserver<PerguntarEvento> stream) {
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
                    .onNext(PerguntarEvento.newBuilder().setAndamento(Andamento.newBuilder()
                            .setEtapa(stage(stage)).setTentativa(attempt)).build()));
            stream.onNext(PerguntarEvento.newBuilder().setResposta(response(result)).build());
            stream.onCompleted();
        } catch (QuestionService.InvalidQuestionException error) {
            stream.onError(Status.INVALID_ARGUMENT.withDescription(error.getMessage()).asRuntimeException());
        } catch (QuestionService.MissingConfigurationException | ProviderCatalog.ModelNotInCatalogException
                | RagKeys.NoKeyPairException error) {
            stream.onError(Status.FAILED_PRECONDITION.withDescription(error.getMessage()).asRuntimeException());
        } catch (KeyEnvelope.UnreadableKeyException error) {
            log.warn("Chave de IA do condomínio {} não pôde ser lida: {}", request.getCondominioId(),
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
            log.error("Falha ao responder a pergunta do condomínio {}: {}", request.getCondominioId(),
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

    private QuestionRequest toRequest(PerguntarRequest request, String authorization) {
        String question = request.getPergunta().strip();
        if (question.isEmpty()) {
            throw new IllegalArgumentException("A pergunta é obrigatória.");
        }
        if (question.length() > QuestionRequest.MAX_CHARS) {
            throw new IllegalArgumentException(
                    "A pergunta passa de " + QuestionRequest.MAX_CHARS + " caracteres.");
        }
        if (request.getLimiteTrechos() < 0) {
            throw new IllegalArgumentException("limite_trechos não pode ser negativo");
        }
        var configuration = request.getConfiguracao();
        if (!embeddings.accepts(configuration.getModeloEmbeddings())) {
            throw new IllegalArgumentException("Modelo de embeddings " + configuration.getModeloEmbeddings()
                    + " não está disponível neste rag (disponível: " + embeddings.model() + ")");
        }
        var filters = filters(request.getCondominioId(), request.getFiltros());
        var mode = configuration.getModoBusca() == ModoBusca.MODO_BUSCA_PALAVRA ? DocumentSearch.Mode.KEYWORD
                : DocumentSearch.Mode.HYBRID;
        var history = request.getHistoricoList().stream()
                .map(t -> new QuestionRequest.Exchange(t.getPergunta(), t.getResposta())).toList();
        return new QuestionRequest(filters, question, history, mode, configuration.getProvedor(),
                configuration.getModelo(), configuration.getChaveCifrada().toByteArray(), request.getLimiteTrechos(),
                authorization);
    }

    private static EtapaPergunta stage(QuestionService.Stage stage) {
        return switch (stage) {
            case SEARCHING_CHUNKS -> EtapaPergunta.ETAPA_PERGUNTA_BUSCANDO_TRECHOS;
            case QUERYING_DATA -> EtapaPergunta.ETAPA_PERGUNTA_CONSULTANDO_DADOS;
            case DRAFTING -> EtapaPergunta.ETAPA_PERGUNTA_REDIGINDO;
            case VALIDATING -> EtapaPergunta.ETAPA_PERGUNTA_VALIDANDO;
            case RETRYING -> EtapaPergunta.ETAPA_PERGUNTA_NOVA_TENTATIVA;
        };
    }

    private static RespostaPergunta response(QuestionResult result) {
        var output = RespostaPergunta.newBuilder()
                .setSituacao(result.status() == QuestionResult.Status.ANSWERED
                        ? SituacaoResposta.SITUACAO_RESPOSTA_RESPONDIDA
                        : SituacaoResposta.SITUACAO_RESPOSTA_NAO_ENCONTRADA)
                .setSugestao(result.suggestion() == null ? "" : result.suggestion())
                .setAviso(result.warning() == null ? "" : result.warning())
                .setUso(usage(result.usage()));
        result.inDocuments().forEach(p -> output.addNosDocumentos(ParagrafoDocumentos.newBuilder()
                .setTexto(p.text()).addAllTrechoIds(p.chunkIds())));
        result.inStoredData().forEach(d -> output.addNosDadosGravados(storedData(d)));
        result.citedChunks().forEach(t -> output.addTrechosCitados(chunk(t)));
        return output.build();
    }

    private static DadoGravado storedData(QuestionResult.DataItem dataItem) {
        QueriedData queried = dataItem.queried();
        var output = DadoGravado.newBuilder()
                .setChamadaId(queried.callId())
                .setConsulta(queried.query())
                .setComentario(dataItem.comment() == null ? "" : dataItem.comment());
        queried.params().forEach(p -> output.addParametros(ParametroConsulta.newBuilder()
                .setNome(p.name()).setValor(p.value())));
        queried.rows().forEach(l -> output.addLinhas(LinhaDado.newBuilder()
                .setRotulo(l.label()).setValor(l.value())));
        return output.build();
    }

    private static UsoPergunta usage(QuestionResult.Usage usage) {
        return UsoPergunta.newBuilder()
                .setTokensEntrada(usage.inputTokens())
                .setTokensSaida(usage.outputTokens())
                .setProvedor(usage.provider())
                .setModelo(usage.model())
                .setVersaoPrompt(usage.promptVersion())
                .setTentativas(usage.attempts())
                .build();
    }

    // -----------------------------------------------------------------------------------------------------------
    // ListarProvedores (delivery 3)
    // -----------------------------------------------------------------------------------------------------------

    @Override
    public void listarProvedores(ListarProvedoresRequest request, StreamObserver<ListarProvedoresResponse> response) {
        String authorization = GrpcAuthorization.AUTHORIZATION.get();
        if (authorization == null || authorization.isBlank()) {
            response.onError(Status.UNAUTHENTICATED
                    .withDescription("Chamada sem token: o metadado authorization é obrigatório.")
                    .asRuntimeException());
            return;
        }
        var output = ListarProvedoresResponse.newBuilder().setChavePublicaPem(keys.publicPem());
        catalog.providers().forEach(p -> output.addProvedores(provider(p)));
        response.onNext(output.build());
        response.onCompleted();
    }

    private static Provedor provider(AiProperties.AiProvider provider) {
        var output = Provedor.newBuilder()
                .setCodigo(provider.code())
                .setNome(provider.name())
                .setTipo(provider.type())
                .setUso(provider.function() == AiProperties.AiFunction.ANSWERS ? UsoProvedor.USO_PROVEDOR_RESPOSTAS
                        : UsoProvedor.USO_PROVEDOR_EMBEDDINGS)
                .setLocal(provider.local())
                .setPrecisaChave(provider.requiresKey())
                .setDimensao(provider.dimension());
        provider.models().forEach(m -> output.addModelos(ModeloProvedor.newBuilder()
                .setId(m.id())
                .setNome(m.name())
                .setPadrao(m.isDefault())
                .setPrecoEntradaMilhaoUsd(m.inputPricePerMillionUsd())
                .setPrecoSaidaMilhaoUsd(m.outputPricePerMillionUsd())));
        return output.build();
    }

    private static IndexRepository.SearchFilters filters(BuscarRequest request) {
        return filters(request.getCondominioId(), request.getFiltros());
    }

    private static IndexRepository.SearchFilters filters(String condominiumId, FiltrosBusca f) {
        UUID condominium = uuid(condominiumId, "condominio_id");
        LocalDate start = date(f.getDataInicio(), "data_inicio");
        LocalDate end = date(f.getDataFim(), "data_fim");
        if (start != null && end != null && start.isAfter(end)) {
            throw new IllegalArgumentException("data_inicio depois de data_fim");
        }
        List<UUID> files = f.getArquivoIdsList().stream().map(id -> uuid(id, "arquivo_ids")).toList();
        List<String> categories = f.getCategoriasList().stream().filter(c -> !c.isBlank()).toList();
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

    public static Trecho chunk(FoundChunk t) {
        var local = br.com.condominioauditoria.contratos.assistente.v1.Localizacao.newBuilder();
        switch (t.location()) {
            case Location.Page l -> local.setPagina(LocalPagina.newBuilder().setPagina(l.number()));
            case Location.Sheet l -> local.setPlanilha(LocalPlanilha.newBuilder().setAba(l.tab())
                    .setLinhaInicio(l.startRow()).setLinhaFim(l.endRow()));
            case Location.Paragraphs l -> local.setParagrafos(LocalParagrafos.newBuilder()
                    .setParagrafoInicio(l.start()).setParagrafoFim(l.end())
                    .setSecao(l.section() == null ? "" : l.section()));
        }
        return Trecho.newBuilder()
                .setTrechoId(t.chunkId().toString())
                .setArquivoId(t.fileId().toString())
                .setNomeArquivo(t.fileName())
                .setCategoria(t.category())
                .setLocalizacao(local)
                .setTexto(t.text())
                .setPontuacao(t.score())
                .setSha256(t.sha256())
                .build();
    }
}
