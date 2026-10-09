package br.com.condominioauditoria.api.grpc;

import br.com.condominioauditoria.api.exception.FeatureNotEnabledException;
import br.com.condominioauditoria.api.model.accounting.Fund;
import br.com.condominioauditoria.api.model.enums.FileCategory;
import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.repository.accounting.FundRepository;
import br.com.condominioauditoria.api.repository.accounting.LedgerEntryRepository;
import br.com.condominioauditoria.api.repository.accounting.TotalsCheckRepository;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import br.com.condominioauditoria.api.repository.file.SourceFileRepository;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.dashboard.DashboardService;
import br.com.condominioauditoria.contratos.consulta.v1.ArquivoResumo;
import br.com.condominioauditoria.contratos.consulta.v1.BuscarDocumentosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.BuscarDocumentosResponse;
import br.com.condominioauditoria.contratos.consulta.v1.CondominioResumo;
import br.com.condominioauditoria.contratos.consulta.v1.Conferencia;
import br.com.condominioauditoria.contratos.consulta.v1.ConferenciasDoArquivoRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ConferenciasDoArquivoResponse;
import br.com.condominioauditoria.contratos.consulta.v1.ConsultaGrpc;
import br.com.condominioauditoria.contratos.consulta.v1.FundoNoPeriodo;
import br.com.condominioauditoria.contratos.consulta.v1.Lancamento;
import br.com.condominioauditoria.contratos.consulta.v1.ListarArquivosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ListarArquivosResponse;
import br.com.condominioauditoria.contratos.consulta.v1.ListarCondominiosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ListarCondominiosResponse;
import br.com.condominioauditoria.contratos.consulta.v1.ListarLancamentosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ResumoFundosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ResumoFundosResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Implementação do contrato contracts/grpc/consulta/v1/consulta.proto. Só leitura. Usa os mesmos serviços e
 * repositórios da API REST, então os números batem com a tela.
 */
@Component
class ConsultaGrpcServico extends ConsultaGrpc.ConsultaImplBase {

    private static final Logger log = LoggerFactory.getLogger(ConsultaGrpcServico.class);
    private static final int LIMITE_PADRAO = 500;
    private static final int LIMITE_MAXIMO = 5000;

    private final CondominiumAccess acesso;
    private final CondominiumRepository condominios;
    private final SourceFileRepository arquivos;
    private final TotalsCheckRepository conferencias;
    private final FundRepository fundos;
    private final LedgerEntryRepository lancamentos;
    private final DashboardService painel;
    private final BuscaDocumentos buscaDocumentos;

    ConsultaGrpcServico(CondominiumAccess acesso, CondominiumRepository condominios, SourceFileRepository arquivos,
            TotalsCheckRepository conferencias, FundRepository fundos, LedgerEntryRepository lancamentos,
            DashboardService painel, BuscaDocumentos buscaDocumentos) {
        this.acesso = acesso;
        this.condominios = condominios;
        this.arquivos = arquivos;
        this.conferencias = conferencias;
        this.fundos = fundos;
        this.lancamentos = lancamentos;
        this.painel = painel;
        this.buscaDocumentos = buscaDocumentos;
    }

    @Override
    public void listarCondominios(ListarCondominiosRequest pedido, StreamObserver<ListarCondominiosResponse> resposta) {
        responder(resposta, () -> ListarCondominiosResponse.newBuilder()
                .addAllCondominios(condominios.findAll().stream()
                        .filter(c -> acesso.canAccess(c.getId()))
                        .map(c -> CondominioResumo.newBuilder().setId(c.getId().toString()).setNome(c.getName()).build())
                        .toList())
                .build());
    }

    @Override
    public void resumoFundos(ResumoFundosRequest pedido, StreamObserver<ResumoFundosResponse> resposta) {
        responder(resposta, () -> {
            UUID condominioId = condominio(pedido.getCondominioId());
            return painel.latest(condominioId).map(p -> ResumoFundosResponse.newBuilder()
                    .setTemDados(true)
                    .setArquivoId(p.fileId().toString())
                    .setArquivoNome(p.fileName())
                    .setPeriodoInicio(texto(p.periodStart()))
                    .setPeriodoFim(texto(p.periodEnd()))
                    .setSaldoAnterior(dinheiro(p.openingBalance()))
                    .setEntradas(dinheiro(p.inflows()))
                    .setSaidas(dinheiro(p.outflows()))
                    .setSaldoAtual(dinheiro(p.closingBalance()))
                    .setConferenciasComFalha((int) p.failedChecks())
                    .addAllFundos(p.funds().stream().map(f -> FundoNoPeriodo.newBuilder()
                            .setFundo(f.fund())
                            .setSaldoAnterior(dinheiro(f.openingBalance()))
                            .setEntradas(dinheiro(f.inflows()))
                            .setSaidas(dinheiro(f.outflows()))
                            .setSaldoAtual(dinheiro(f.closingBalance()))
                            .build()).toList())
                    .build())
                    .orElse(ResumoFundosResponse.newBuilder().setTemDados(false).build());
        });
    }

    @Override
    public void listarArquivos(ListarArquivosRequest pedido, StreamObserver<ListarArquivosResponse> resposta) {
        responder(resposta, () -> {
            UUID condominioId = condominio(pedido.getCondominioId());
            int limite = limite(pedido.getLimite(), 50, 500);
            List<SourceFile> lista = pedido.getCategoria().isBlank()
                    ? arquivos.findByCondominiumIdOrderByUploadedAtDesc(condominioId)
                    : arquivos.findByCondominiumIdAndCategoryOrderByUploadedAtDesc(condominioId, categoria(pedido.getCategoria()));
            return ListarArquivosResponse.newBuilder()
                    .addAllArquivos(lista.stream().limit(limite).map(ConsultaGrpcServico::resumo).toList())
                    .build();
        });
    }

    @Override
    public void conferenciasDoArquivo(ConferenciasDoArquivoRequest pedido,
            StreamObserver<ConferenciasDoArquivoResponse> resposta) {
        responder(resposta, () -> {
            UUID condominioId = condominio(pedido.getCondominioId());
            UUID arquivoId = uuid(pedido.getArquivoId(), "arquivo_id");
            arquivos.findByIdAndCondominiumId(arquivoId, condominioId)
                    .orElseThrow(() -> Status.NOT_FOUND.withDescription("Arquivo não encontrado").asRuntimeException());
            return ConferenciasDoArquivoResponse.newBuilder()
                    .addAllConferencias(conferencias.findByFileIdOrderBySequence(arquivoId).stream()
                            .map(c -> Conferencia.newBuilder().setCodigo(c.getCode()).setDescricao(c.getDescription())
                                    .setOk(c.isOk()).setDetalhe(Objects.toString(c.getDetail(), "")).build())
                            .toList())
                    .build();
        });
    }

    /** Resposta em fluxo: cada lançamento sai assim que é convertido, sem montar a lista inteira no cliente. */
    @Override
    public void listarLancamentos(ListarLancamentosRequest pedido, StreamObserver<Lancamento> resposta) {
        try {
            UUID condominioId = condominio(pedido.getCondominioId());
            Map<UUID, String> nomes = fundos.findByCondominiumId(condominioId).stream()
                    .collect(Collectors.toMap(Fund::getId, Fund::getName));
            String filtroFundo = pedido.getFundo().trim().toLowerCase();
            List<UUID> fundosFiltrados = filtroFundo.isEmpty() ? List.of(UUID.randomUUID())
                    : nomes.entrySet().stream().filter(e -> e.getValue().toLowerCase().contains(filtroFundo))
                            .map(Map.Entry::getKey).toList();
            if (!filtroFundo.isEmpty() && fundosFiltrados.isEmpty()) {
                resposta.onCompleted();
                return;
            }
            String texto = pedido.getTexto().isBlank() ? "" : "%" + pedido.getTexto().trim().toLowerCase() + "%";
            var lista = lancamentos.search(condominioId,
                    data(pedido.getDataInicio(), LocalDate.of(1900, 1, 1)),
                    data(pedido.getDataFim(), LocalDate.of(9999, 12, 31)),
                    filtroFundo.isEmpty(), fundosFiltrados, texto, pedido.getSomenteSaidas(),
                    Limit.of(limite(pedido.getLimite(), LIMITE_PADRAO, LIMITE_MAXIMO)));
            for (var l : lista) {
                resposta.onNext(Lancamento.newBuilder()
                        .setData(texto(l.getDate()))
                        .setFundo(Objects.toString(nomes.get(l.getFundId()), ""))
                        .setContaCodigo(Objects.toString(l.getAccountCode(), ""))
                        .setContaNome(Objects.toString(l.getAccountName(), ""))
                        .setDocumento(Objects.toString(l.getDocument(), ""))
                        .setHistorico(l.getMemo())
                        .setCredito(dinheiro(l.getCredit()))
                        .setDebito(dinheiro(l.getDebit()))
                        .setSaldo(dinheiro(l.getBalance()))
                        .setFornecedor(Objects.toString(l.getSupplier(), ""))
                        .setNotaFiscal(Objects.toString(l.getInvoiceNumber(), ""))
                        .setMeioPagamento(Objects.toString(l.getPaymentMethod(), ""))
                        .setTransferenciaEntreFundos(l.isInterFundTransfer())
                        .setArquivoId(l.getFileId().toString())
                        .setPagina(l.getPage())
                        .build());
            }
            resposta.onCompleted();
        } catch (RuntimeException erro) {
            resposta.onError(traduzir(erro));
        }
    }

    /**
     * Busca trechos nos documentos do condomínio, pelo rag (ADR 0003, Decisão 5.3). Mesma verificação de token, perfil
     * e condomínio dos outros rpcs; o rag ainda filtra pelo condomínio e o backend descarta, na volta, trechos de
     * arquivos que não são do condomínio.
     */
    @Override
    public void buscarDocumentos(BuscarDocumentosRequest pedido, StreamObserver<BuscarDocumentosResponse> resposta) {
        responder(resposta, () -> buscaDocumentos.buscar(condominio(pedido.getCondominioId()), pedido));
    }

    // ---- apoio ----

    private <T> void responder(StreamObserver<T> resposta, java.util.function.Supplier<T> acao) {
        try {
            resposta.onNext(acao.get());
            resposta.onCompleted();
        } catch (RuntimeException erro) {
            resposta.onError(traduzir(erro));
        }
    }

    private StatusRuntimeException traduzir(RuntimeException erro) {
        return switch (erro) {
            case StatusRuntimeException s -> s;
            case AccessDeniedException a -> Status.PERMISSION_DENIED.withDescription(a.getMessage()).asRuntimeException();
            case FeatureNotEnabledException m ->
                    Status.FAILED_PRECONDITION.withDescription(m.getMessage()).asRuntimeException();
            case IllegalArgumentException i -> Status.INVALID_ARGUMENT.withDescription(i.getMessage()).asRuntimeException();
            default -> {
                log.error("Erro no gRPC de consulta", erro);
                yield Status.INTERNAL.withDescription("Erro interno no backend").asRuntimeException();
            }
        };
    }

    private UUID condominio(String id) {
        UUID condominioId = uuid(id, "condominio_id");
        acesso.require(condominioId);
        return condominioId;
    }

    private static UUID uuid(String valor, String campo) {
        try {
            return UUID.fromString(valor.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(campo + " inválido: '" + valor + "'");
        }
    }

    private static FileCategory categoria(String valor) {
        try {
            return FileCategory.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Categoria desconhecida: " + valor);
        }
    }

    private static LocalDate data(String valor, LocalDate padrao) {
        if (valor.isBlank()) {
            return padrao;
        }
        try {
            return LocalDate.parse(valor.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Data inválida (use AAAA-MM-DD): " + valor);
        }
    }

    private static int limite(int pedido, int padrao, int maximo) {
        return pedido <= 0 ? padrao : Math.min(pedido, maximo);
    }

    private static String dinheiro(BigDecimal valor) {
        return valor == null ? "" : valor.setScale(2).toPlainString();
    }

    private static String texto(Object valor) {
        return valor == null ? "" : valor.toString();
    }

    private static ArquivoResumo resumo(SourceFile a) {
        return ArquivoResumo.newBuilder()
                .setId(a.getId().toString())
                .setCategoria(a.getCategory().name())
                .setNome(a.getOriginalName())
                .setStatus(a.getStatus().name())
                .setMensagem(Objects.toString(a.getMessage(), ""))
                .setPeriodoInicio(texto(a.getPeriodStart()))
                .setPeriodoFim(texto(a.getPeriodEnd()))
                .setTotalLancamentos(a.getEntryCount() == null ? 0 : a.getEntryCount())
                .setEnviadoPor(a.getUploadedBy())
                .setEnviadoEm(texto(a.getUploadedAt()))
                .build();
    }
}
