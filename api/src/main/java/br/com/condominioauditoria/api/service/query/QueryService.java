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
import br.com.condominioauditoria.contracts.query.v2.FileSummary;
import br.com.condominioauditoria.contracts.query.v2.SearchDocumentsRequest;
import br.com.condominioauditoria.contracts.query.v2.SearchDocumentsResponse;
import br.com.condominioauditoria.contracts.query.v2.CondominiumSummary;
import br.com.condominioauditoria.contracts.query.v2.FileCheck;
import br.com.condominioauditoria.contracts.query.v2.FileChecksRequest;
import br.com.condominioauditoria.contracts.query.v2.FileChecksResponse;
import br.com.condominioauditoria.contracts.query.v2.FundInPeriod;
import br.com.condominioauditoria.contracts.query.v2.Entry;
import br.com.condominioauditoria.contracts.query.v2.ListFilesRequest;
import br.com.condominioauditoria.contracts.query.v2.ListFilesResponse;
import br.com.condominioauditoria.contracts.query.v2.ListCondominiumsResponse;
import br.com.condominioauditoria.contracts.query.v2.ListEntriesRequest;
import br.com.condominioauditoria.contracts.query.v2.FundSummaryRequest;
import br.com.condominioauditoria.contracts.query.v2.FundSummaryResponse;
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
 * Read-only queries of contracts/grpc/query/v2/query.proto, called by the mcp and by the rag with the user's
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
    public ListCondominiumsResponse condominiums() {
        return ListCondominiumsResponse.newBuilder()
                .addAllCondominiums(condominiums.findAll().stream()
                        .filter(c -> access.canAccess(c.getId()))
                        .map(c -> CondominiumSummary.newBuilder().setId(c.getId().toString()).setName(c.getName())
                                .build())
                        .toList())
                .build();
    }

    /** The latest dashboard numbers, per fund; temDados false when no file was processed yet. */
    public FundSummaryResponse fundSummary(FundSummaryRequest request) {
        UUID condominiumId = condominium(request.getCondominiumId());
        return dashboard.latest(condominiumId).map(p -> FundSummaryResponse.newBuilder()
                .setHasData(true)
                .setFileId(p.fileId().toString())
                .setFileName(p.fileName())
                .setPeriodStart(text(p.periodStart()))
                .setPeriodEnd(text(p.periodEnd()))
                .setOpeningBalance(money(p.openingBalance()))
                .setInflows(money(p.inflows()))
                .setOutflows(money(p.outflows()))
                .setClosingBalance(money(p.closingBalance()))
                .setFailedChecks((int) p.failedChecks())
                .addAllFunds(p.funds().stream().map(f -> FundInPeriod.newBuilder()
                        .setFund(f.fund())
                        .setOpeningBalance(money(f.openingBalance()))
                        .setInflows(money(f.inflows()))
                        .setOutflows(money(f.outflows()))
                        .setClosingBalance(money(f.closingBalance()))
                        .build()).toList())
                .build())
                .orElse(FundSummaryResponse.newBuilder().setHasData(false).build());
    }

    /** The condominium's files, newest first, optionally of one category. */
    public ListFilesResponse files(ListFilesRequest request) {
        UUID condominiumId = condominium(request.getCondominiumId());
        int limit = limit(request.getLimit(), 50, 500);
        List<SourceFile> list = request.getCategory().isBlank()
                ? files.findByCondominiumIdOrderByUploadedAtDesc(condominiumId)
                : files.findByCondominiumIdAndCategoryOrderByUploadedAtDesc(condominiumId,
                        category(request.getCategory()));
        return ListFilesResponse.newBuilder()
                .addAllFiles(list.stream().limit(limit).map(QueryService::summary).toList())
                .build();
    }

    /** The totals checks of one file of the condominium. */
    public FileChecksResponse fileChecks(FileChecksRequest request) {
        UUID condominiumId = condominium(request.getCondominiumId());
        UUID fileId = uuid(request.getFileId(), "arquivo_id");
        files.findByIdAndCondominiumId(fileId, condominiumId)
                .orElseThrow(() -> Status.NOT_FOUND.withDescription("Arquivo não encontrado").asRuntimeException());
        return FileChecksResponse.newBuilder()
                .addAllChecks(totalsChecks.findByFileIdOrderBySequence(fileId).stream()
                        .map(c -> FileCheck.newBuilder().setCode(c.getCode()).setDescription(c.getDescription())
                                .setOk(c.isOk()).setDetail(Objects.toString(c.getDetail(), "")).build())
                        .toList())
                .build();
    }

    /**
     * Ledger entries matching the filters. The stream converts each entry only when it is read, so the caller can send
     * each one as soon as it is ready, without building the whole list of messages.
     */
    public Stream<Entry> ledgerEntries(ListEntriesRequest request) {
        UUID condominiumId = condominium(request.getCondominiumId());
        Map<UUID, String> names = funds.findByCondominiumId(condominiumId).stream()
                .collect(Collectors.toMap(Fund::getId, Fund::getName));
        String fundFilter = request.getFund().trim().toLowerCase();
        List<UUID> filteredFunds = fundFilter.isEmpty() ? List.of(UUID.randomUUID())
                : names.entrySet().stream().filter(e -> e.getValue().toLowerCase().contains(fundFilter))
                        .map(Map.Entry::getKey).toList();
        if (!fundFilter.isEmpty() && filteredFunds.isEmpty()) {
            return Stream.empty();
        }
        String text = request.getText().isBlank() ? "" : "%" + request.getText().trim().toLowerCase() + "%";
        var list = ledgerEntries.search(condominiumId,
                date(request.getDateFrom(), LocalDate.of(1900, 1, 1)),
                date(request.getDateTo(), LocalDate.of(9999, 12, 31)),
                fundFilter.isEmpty(), filteredFunds, text, request.getOutflowsOnly(),
                Limit.of(limit(request.getLimit(), DEFAULT_LIMIT, MAX_LIMIT)));
        return list.stream().map(l -> Entry.newBuilder()
                .setDate(text(l.getDate()))
                .setFund(Objects.toString(names.get(l.getFundId()), ""))
                .setAccountCode(Objects.toString(l.getAccountCode(), ""))
                .setAccountName(Objects.toString(l.getAccountName(), ""))
                .setDocument(Objects.toString(l.getDocument(), ""))
                .setMemo(l.getMemo())
                .setCredit(money(l.getCredit()))
                .setDebit(money(l.getDebit()))
                .setBalance(money(l.getBalance()))
                .setSupplier(Objects.toString(l.getSupplier(), ""))
                .setInvoiceNumber(Objects.toString(l.getInvoiceNumber(), ""))
                .setPaymentMethod(Objects.toString(l.getPaymentMethod(), ""))
                .setInterFundTransfer(l.isInterFundTransfer())
                .setFileId(l.getFileId().toString())
                .setPage(l.getPage())
                .build());
    }

    /**
     * Searches chunks in the condominium's documents through the rag (ADR 0003, Decision 5.3). Same token, role and
     * condominium checks as the other queries; the rag also filters by condominium, and on the way back the api drops
     * chunks of files that are not the condominium's.
     */
    public SearchDocumentsResponse searchDocuments(SearchDocumentsRequest request) {
        return documentSearch.search(condominium(request.getCondominiumId()), request);
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

    private static FileSummary summary(SourceFile file) {
        return FileSummary.newBuilder()
                .setId(file.getId().toString())
                .setCategory(file.getCategory().name())
                .setName(file.getOriginalName())
                .setStatus(file.getStatus().name())
                .setMessage(Objects.toString(file.getMessage(), ""))
                .setPeriodStart(text(file.getPeriodStart()))
                .setPeriodEnd(text(file.getPeriodEnd()))
                .setEntryCount(file.getEntryCount() == null ? 0 : file.getEntryCount())
                .setUploadedBy(file.getUploadedBy())
                .setUploadedAt(text(file.getUploadedAt()))
                .build();
    }
}
