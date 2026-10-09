package br.com.condominioauditoria.api.service.query;

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
import br.com.condominioauditoria.contratos.consulta.v1.FundoNoPeriodo;
import br.com.condominioauditoria.contratos.consulta.v1.Lancamento;
import br.com.condominioauditoria.contratos.consulta.v1.ListarArquivosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ListarArquivosResponse;
import br.com.condominioauditoria.contratos.consulta.v1.ListarCondominiosResponse;
import br.com.condominioauditoria.contratos.consulta.v1.ListarLancamentosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ResumoFundosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ResumoFundosResponse;
import io.grpc.Status;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

/**
 * Read-only queries of contracts/grpc/consulta/v1/consulta.proto, called by the mcp and by the rag with the user's
 * token. Uses the same services and repositories as the REST API, so the numbers match the screen. Every query that
 * names a condominium checks the user's access to it first; an invalid argument is an {@link IllegalArgumentException}.
 */
@Service
public class QueryService {

    private static final int DEFAULT_LIMIT = 500;
    private static final int MAX_LIMIT = 5000;

    private final CondominiumAccess access;
    private final CondominiumRepository condominiums;
    private final SourceFileRepository files;
    private final TotalsCheckRepository totalsChecks;
    private final FundRepository funds;
    private final LedgerEntryRepository ledgerEntries;
    private final DashboardService dashboard;
    private final DocumentSearchService documentSearch;

    public QueryService(CondominiumAccess access, CondominiumRepository condominiums, SourceFileRepository files,
            TotalsCheckRepository totalsChecks, FundRepository funds, LedgerEntryRepository ledgerEntries,
            DashboardService dashboard, DocumentSearchService documentSearch) {
        this.access = access;
        this.condominiums = condominiums;
        this.files = files;
        this.totalsChecks = totalsChecks;
        this.funds = funds;
        this.ledgerEntries = ledgerEntries;
        this.dashboard = dashboard;
        this.documentSearch = documentSearch;
    }

    /** The condominiums the user can access. */
    public ListarCondominiosResponse condominiums() {
        return ListarCondominiosResponse.newBuilder()
                .addAllCondominios(condominiums.findAll().stream()
                        .filter(c -> access.canAccess(c.getId()))
                        .map(c -> CondominioResumo.newBuilder().setId(c.getId().toString()).setNome(c.getName())
                                .build())
                        .toList())
                .build();
    }

    /** The latest dashboard numbers, per fund; temDados false when no file was processed yet. */
    public ResumoFundosResponse fundSummary(ResumoFundosRequest request) {
        UUID condominiumId = condominium(request.getCondominioId());
        return dashboard.latest(condominiumId).map(p -> ResumoFundosResponse.newBuilder()
                .setTemDados(true)
                .setArquivoId(p.fileId().toString())
                .setArquivoNome(p.fileName())
                .setPeriodoInicio(text(p.periodStart()))
                .setPeriodoFim(text(p.periodEnd()))
                .setSaldoAnterior(money(p.openingBalance()))
                .setEntradas(money(p.inflows()))
                .setSaidas(money(p.outflows()))
                .setSaldoAtual(money(p.closingBalance()))
                .setConferenciasComFalha((int) p.failedChecks())
                .addAllFundos(p.funds().stream().map(f -> FundoNoPeriodo.newBuilder()
                        .setFundo(f.fund())
                        .setSaldoAnterior(money(f.openingBalance()))
                        .setEntradas(money(f.inflows()))
                        .setSaidas(money(f.outflows()))
                        .setSaldoAtual(money(f.closingBalance()))
                        .build()).toList())
                .build())
                .orElse(ResumoFundosResponse.newBuilder().setTemDados(false).build());
    }

    /** The condominium's files, newest first, optionally of one category. */
    public ListarArquivosResponse files(ListarArquivosRequest request) {
        UUID condominiumId = condominium(request.getCondominioId());
        int limit = limit(request.getLimite(), 50, 500);
        List<SourceFile> list = request.getCategoria().isBlank()
                ? files.findByCondominiumIdOrderByUploadedAtDesc(condominiumId)
                : files.findByCondominiumIdAndCategoryOrderByUploadedAtDesc(condominiumId,
                        category(request.getCategoria()));
        return ListarArquivosResponse.newBuilder()
                .addAllArquivos(list.stream().limit(limit).map(QueryService::summary).toList())
                .build();
    }

    /** The totals checks of one file of the condominium. */
    public ConferenciasDoArquivoResponse fileChecks(ConferenciasDoArquivoRequest request) {
        UUID condominiumId = condominium(request.getCondominioId());
        UUID fileId = uuid(request.getArquivoId(), "arquivo_id");
        files.findByIdAndCondominiumId(fileId, condominiumId)
                .orElseThrow(() -> Status.NOT_FOUND.withDescription("Arquivo não encontrado").asRuntimeException());
        return ConferenciasDoArquivoResponse.newBuilder()
                .addAllConferencias(totalsChecks.findByFileIdOrderBySequence(fileId).stream()
                        .map(c -> Conferencia.newBuilder().setCodigo(c.getCode()).setDescricao(c.getDescription())
                                .setOk(c.isOk()).setDetalhe(Objects.toString(c.getDetail(), "")).build())
                        .toList())
                .build();
    }

    /**
     * Ledger entries matching the filters. The stream converts each entry only when it is read, so the caller can send
     * each one as soon as it is ready, without building the whole list of messages.
     */
    public Stream<Lancamento> ledgerEntries(ListarLancamentosRequest request) {
        UUID condominiumId = condominium(request.getCondominioId());
        Map<UUID, String> names = funds.findByCondominiumId(condominiumId).stream()
                .collect(Collectors.toMap(Fund::getId, Fund::getName));
        String fundFilter = request.getFundo().trim().toLowerCase();
        List<UUID> filteredFunds = fundFilter.isEmpty() ? List.of(UUID.randomUUID())
                : names.entrySet().stream().filter(e -> e.getValue().toLowerCase().contains(fundFilter))
                        .map(Map.Entry::getKey).toList();
        if (!fundFilter.isEmpty() && filteredFunds.isEmpty()) {
            return Stream.empty();
        }
        String text = request.getTexto().isBlank() ? "" : "%" + request.getTexto().trim().toLowerCase() + "%";
        var list = ledgerEntries.search(condominiumId,
                date(request.getDataInicio(), LocalDate.of(1900, 1, 1)),
                date(request.getDataFim(), LocalDate.of(9999, 12, 31)),
                fundFilter.isEmpty(), filteredFunds, text, request.getSomenteSaidas(),
                Limit.of(limit(request.getLimite(), DEFAULT_LIMIT, MAX_LIMIT)));
        return list.stream().map(l -> Lancamento.newBuilder()
                .setData(text(l.getDate()))
                .setFundo(Objects.toString(names.get(l.getFundId()), ""))
                .setContaCodigo(Objects.toString(l.getAccountCode(), ""))
                .setContaNome(Objects.toString(l.getAccountName(), ""))
                .setDocumento(Objects.toString(l.getDocument(), ""))
                .setHistorico(l.getMemo())
                .setCredito(money(l.getCredit()))
                .setDebito(money(l.getDebit()))
                .setSaldo(money(l.getBalance()))
                .setFornecedor(Objects.toString(l.getSupplier(), ""))
                .setNotaFiscal(Objects.toString(l.getInvoiceNumber(), ""))
                .setMeioPagamento(Objects.toString(l.getPaymentMethod(), ""))
                .setTransferenciaEntreFundos(l.isInterFundTransfer())
                .setArquivoId(l.getFileId().toString())
                .setPagina(l.getPage())
                .build());
    }

    /**
     * Searches chunks in the condominium's documents through the rag (ADR 0003, Decision 5.3). Same token, role and
     * condominium checks as the other queries; the rag also filters by condominium, and on the way back the api drops
     * chunks of files that are not the condominium's.
     */
    public BuscarDocumentosResponse searchDocuments(BuscarDocumentosRequest request) {
        return documentSearch.search(condominium(request.getCondominioId()), request);
    }

    private UUID condominium(String id) {
        UUID condominiumId = uuid(id, "condominio_id");
        access.require(condominiumId);
        return condominiumId;
    }

    private static UUID uuid(String value, String field) {
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(field + " inválido: '" + value + "'");
        }
    }

    private static FileCategory category(String value) {
        try {
            return FileCategory.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Categoria desconhecida: " + value);
        }
    }

    private static LocalDate date(String value, LocalDate defaultValue) {
        if (value.isBlank()) {
            return defaultValue;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Data inválida (use AAAA-MM-DD): " + value);
        }
    }

    private static int limit(int requested, int defaultValue, int max) {
        return requested <= 0 ? defaultValue : Math.min(requested, max);
    }

    private static String money(BigDecimal value) {
        return value == null ? "" : value.setScale(2).toPlainString();
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static ArquivoResumo summary(SourceFile file) {
        return ArquivoResumo.newBuilder()
                .setId(file.getId().toString())
                .setCategoria(file.getCategory().name())
                .setNome(file.getOriginalName())
                .setStatus(file.getStatus().name())
                .setMensagem(Objects.toString(file.getMessage(), ""))
                .setPeriodoInicio(text(file.getPeriodStart()))
                .setPeriodoFim(text(file.getPeriodEnd()))
                .setTotalLancamentos(file.getEntryCount() == null ? 0 : file.getEntryCount())
                .setEnviadoPor(file.getUploadedBy())
                .setEnviadoEm(text(file.getUploadedAt()))
                .build();
    }
}
