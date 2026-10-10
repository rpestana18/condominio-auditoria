package br.com.condominioauditoria.mcp.tool;

import br.com.condominioauditoria.contracts.query.v2.SearchDocumentsRequest;
import br.com.condominioauditoria.contracts.query.v2.FileChecksRequest;
import br.com.condominioauditoria.contracts.query.v2.DocumentFilters;
import br.com.condominioauditoria.contracts.query.v2.ListFilesRequest;
import br.com.condominioauditoria.contracts.query.v2.ListCondominiumsRequest;
import br.com.condominioauditoria.contracts.query.v2.ListEntriesRequest;
import br.com.condominioauditoria.contracts.query.v2.DocumentChunkLocation;
import br.com.condominioauditoria.contracts.query.v2.FundSummaryRequest;
import br.com.condominioauditoria.contracts.query.v2.DocumentChunk;
import br.com.condominioauditoria.mcp.client.ApiClient;
import io.grpc.StatusRuntimeException;
import io.modelcontextprotocol.common.McpTransportContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * Tools the external AI sees. All are read-only and answer with the numbers already stored and checked by the api,
 * with the origin (file and page) for the user to check. Amounts in reais come as text with two decimal places
 * ("1234.56"). The exception is search_documents, which returns document text to cite (ADR 0003, 5.3).
 *
 * The tool names, the parameter names (read by Spring AI from the Java parameters) and the JSON fields of the
 * responses (the record components) are the interface the AI sees. The descriptions stay in Portuguese: they are
 * the text the AI reads to choose a tool.
 */
@Component
public class CondominiumTools {

    private final ApiClient api;

    public CondominiumTools(ApiClient api) {
        this.api = api;
    }

    @McpTool(name = "list_condominiums",
            description = "Lista os condomínios que o usuário pode consultar, com o id usado nas outras ferramentas.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public List<Condominium> listCondominiums(McpTransportContext context) {
        return call(() -> api.query(context)
                .listCondominiums(ListCondominiumsRequest.getDefaultInstance())
                .getCondominiumsList().stream()
                .map(c -> new Condominium(c.getId(), c.getName()))
                .toList());
    }

    @McpTool(name = "fund_summary",
            description = "Saldo anterior, entradas, saídas e saldo atual de cada fundo no fluxo de caixa mais recente "
                    + "do condomínio, mais o total e quantas conferências aritméticas falharam.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public FundSummary fundSummary(McpTransportContext context,
            @McpToolParam(description = "Id do condomínio (veja list_condominiums)") String condominiumId) {
        return call(() -> {
            var r = api.query(context).fundSummary(
                    FundSummaryRequest.newBuilder().setCondominiumId(condominiumId).build());
            if (!r.getHasData()) {
                return new FundSummary(false, null, null, null, null, null, null, null, 0, List.of());
            }
            return new FundSummary(true, r.getFileName(), r.getPeriodStart(), r.getPeriodEnd(),
                    r.getOpeningBalance(), r.getInflows(), r.getOutflows(), r.getClosingBalance(),
                    r.getFailedChecks(),
                    r.getFundsList().stream().map(f -> new Fund(f.getFund(), f.getOpeningBalance(), f.getInflows(),
                            f.getOutflows(), f.getClosingBalance())).toList());
        });
    }

    @McpTool(name = "list_files",
            description = "Arquivos enviados ao sistema, do mais recente para o mais antigo, com o status da leitura.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public List<FileInfo> listFiles(McpTransportContext context,
            @McpToolParam(description = "Id do condomínio") String condominiumId,
            @McpToolParam(required = false, description = "Categoria: TRIAL_BALANCE, BANK_STATEMENT, PO, CONTRACT, PAYROLL, "
                    + "RECEIPT, MINUTES, BYLAWS ou OTHER") String category,
            @McpToolParam(required = false, description = "Quantos arquivos no máximo (padrão 50)") Integer limit) {
        return call(() -> api.query(context).listFiles(ListFilesRequest.newBuilder()
                        .setCondominiumId(condominiumId)
                        .setCategory(Objects.toString(category, ""))
                        .setLimit(limit == null ? 0 : limit)
                        .build())
                .getFilesList().stream()
                .map(a -> new FileInfo(a.getId(), a.getCategory(), a.getName(), a.getStatus(), a.getMessage(),
                        a.getPeriodStart(), a.getPeriodEnd(), a.getEntryCount(), a.getUploadedAt()))
                .toList());
    }

    @McpTool(name = "file_checks",
            description = "Resultado das conferências aritméticas de um arquivo lido (saldo linha a linha, totais por "
                    + "fundo, saldo final e total da posição financeira), com o detalhe quando algo não bate.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public List<Check> fileChecks(McpTransportContext context,
            @McpToolParam(description = "Id do condomínio") String condominiumId,
            @McpToolParam(description = "Id do arquivo (veja list_files)") String fileId) {
        return call(() -> api.query(context).fileChecks(FileChecksRequest.newBuilder()
                        .setCondominiumId(condominiumId).setFileId(fileId).build())
                .getChecksList().stream()
                .map(c -> new Check(c.getCode(), c.getDescription(), c.getOk(), c.getDetail()))
                .toList());
    }

    @McpTool(name = "find_entries",
            description = "Lançamentos do fluxo de caixa com filtros por período, fundo e texto (histórico, fornecedor "
                    + "ou conta). Cada lançamento traz o arquivo e a página de origem.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public List<LedgerEntry> findEntries(McpTransportContext context,
            @McpToolParam(description = "Id do condomínio") String condominiumId,
            @McpToolParam(required = false, description = "Data inicial, AAAA-MM-DD") String dateFrom,
            @McpToolParam(required = false, description = "Data final, AAAA-MM-DD") String dateTo,
            @McpToolParam(required = false, description = "Parte do nome do fundo") String fund,
            @McpToolParam(required = false, description = "Parte do histórico, do fornecedor ou da conta") String text,
            @McpToolParam(required = false, description = "true para só saídas, sem transferências entre fundos")
            Boolean outflowsOnly,
            @McpToolParam(required = false, description = "Quantos lançamentos no máximo (padrão 500, máximo 5000)")
            Integer limit) {
        return call(() -> {
            var request = ListEntriesRequest.newBuilder()
                    .setCondominiumId(condominiumId)
                    .setDateFrom(Objects.toString(dateFrom, ""))
                    .setDateTo(Objects.toString(dateTo, ""))
                    .setFund(Objects.toString(fund, ""))
                    .setText(Objects.toString(text, ""))
                    .setOutflowsOnly(Boolean.TRUE.equals(outflowsOnly))
                    .setLimit(limit == null ? 0 : limit)
                    .build();
            List<LedgerEntry> list = new ArrayList<>();
            api.query(context).listEntries(request).forEachRemaining(l -> list.add(new LedgerEntry(
                    l.getDate(), l.getFund(), l.getAccountCode(), l.getAccountName(), l.getMemo(), l.getCredit(),
                    l.getDebit(), l.getSupplier(), l.getPaymentMethod(), l.getInterFundTransfer(),
                    l.getFileId(), l.getPage())));
            return list;
        });
    }

    @McpTool(name = "search_documents",
            description = "Busca trechos nos documentos enviados do condomínio (atas, contratos, convenção, extratos, "
                    + "balancetes, planilhas e outros) para responder o que está escrito neles. Cada trecho vem com o "
                    + "nome do documento, a categoria e a localização no original (\"página 3\", \"aba Plan1, linhas "
                    + "2–31\" ou \"parágrafos 4–7\"): cite sempre essa localização ao usar o trecho. O texto é "
                    + "transcrição literal e não foi conferido: valores que aparecem nele não servem para somar ou "
                    + "comparar; para números use fund_summary, find_entries e file_checks. "
                    + "Sem resultado não prova que algo não existe: só que não foi achado nos documentos indexados. "
                    + "Só aparecem arquivos já indexados (situação da indexação na lista de arquivos).",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public DocumentSearchResult searchDocuments(McpTransportContext context,
            @McpToolParam(description = "Id do condomínio (veja list_condominiums)") String condominiumId,
            @McpToolParam(description = "O que procurar, em português. Palavras soltas acham qualquer forma "
                    + "(com ou sem acento); \"frase entre aspas\" exige a frase exata; -palavra exclui trechos com "
                    + "ela. Ex.: \"reajuste da taxa\" -2023") String text,
            @McpToolParam(required = false, description = "Só estas categorias: TRIAL_BALANCE, BANK_STATEMENT, PO, CONTRACT, "
                    + "PAYROLL, RECEIPT, MINUTES, BYLAWS ou OTHER. Vazio = todas") List<String> categories,
            @McpToolParam(required = false, description = "Só documentos com competência a partir desta data, "
                    + "AAAA-MM-DD") String dateFrom,
            @McpToolParam(required = false, description = "Só documentos com competência até esta data, AAAA-MM-DD")
            String dateTo,
            @McpToolParam(required = false, description = "Só estes arquivos (ids de list_files). Vazio = todos")
            List<String> fileIds,
            @McpToolParam(required = false, description = "Quantos trechos no máximo, de 1 a 50 (padrão 10)")
            Integer limit) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Informe o texto da busca");
        }
        if (limit != null && limit < 1) {
            throw new IllegalArgumentException("O limite vai de 1 a 50; recebido " + limit);
        }
        var filters = DocumentFilters.newBuilder()
                .addAllCategories(withoutBlanks(categories))
                .setDateFrom(Objects.toString(dateFrom, "").strip())
                .setDateTo(Objects.toString(dateTo, "").strip())
                .addAllFileIds(withoutBlanks(fileIds))
                .build();
        var request = SearchDocumentsRequest.newBuilder()
                .setCondominiumId(Objects.toString(condominiumId, "").strip())
                .setText(text.strip())
                .setFilters(filters)
                .setLimit(limit == null ? DEFAULT_SEARCH_LIMIT : Math.min(limit, MAX_SEARCH_LIMIT))
                .build();
        return call(() -> {
            var r = api.query(context).searchDocuments(request);
            String mode = switch (r.getModeUsed()) {
                case DOCUMENT_SEARCH_MODE_KEYWORD -> "KEYWORD";
                case DOCUMENT_SEARCH_MODE_HYBRID -> "HYBRID";
                default -> "NOT_REPORTED";
            };
            List<FoundChunk> chunks = r.getChunksList().stream().map(CondominiumTools::chunk).toList();
            return new DocumentSearchResult(mode, chunks.size(), chunks, SEARCH_WARNING);
        });
    }

    private static final int DEFAULT_SEARCH_LIMIT = 10;
    private static final int MAX_SEARCH_LIMIT = 50;
    private static final String SEARCH_WARNING = "Texto transcrito dos documentos, não conferido. Cite documento e "
            + "localização. Para valores, use as ferramentas numéricas.";

    private static List<String> withoutBlanks(List<String> values) {
        return values == null ? List.of()
                : values.stream().filter(Objects::nonNull).map(String::strip).filter(v -> !v.isEmpty()).toList();
    }

    public static FoundChunk chunk(DocumentChunk t) {
        Integer page = t.getLocation().hasPage() ? t.getLocation().getPage().getPage() : null;
        return new FoundChunk(t.getFileName(), t.getFileId(), t.getCategory(),
                readableLocation(t.getLocation()), page, t.getText(), t.getSha256(), t.getChunkId());
    }

    /** "página 3", "aba Plan1, linhas 2–31" or "parágrafos 4–7, seção Cláusula 5". */
    public static String readableLocation(DocumentChunkLocation l) {
        return switch (l.getKindCase()) {
            case PAGE -> "página " + l.getPage().getPage();
            case SHEET -> "aba " + l.getSheet().getTab() + ", "
                    + range("linha", "linhas", l.getSheet().getStartRow(), l.getSheet().getEndRow());
            case PARAGRAPHS -> range("parágrafo", "parágrafos", l.getParagraphs().getParagraphStart(),
                    l.getParagraphs().getParagraphEnd())
                    + (l.getParagraphs().getSection().isBlank() ? "" : ", seção " + l.getParagraphs().getSection());
            case KIND_NOT_SET -> "localização não informada";
        };
    }

    private static String range(String singular, String plural, int start, int end) {
        return end <= start ? singular + " " + start : plural + " " + start + "–" + end;
    }

    private static <T> T call(Supplier<T> call) {
        try {
            return call.get();
        } catch (StatusRuntimeException error) {
            throw ApiClient.translate(error);
        }
    }

    // ---- responses (become JSON for the AI) ----

    public record Condominium(String id, String name) {
    }

    public record FundSummary(
            boolean hasData,
            String file,
            String periodStart,
            String periodEnd,
            String openingBalance,
            String inflows,
            String outflows,
            String closingBalance,
            int failedChecks,
            List<Fund> funds) {
    }

    public record Fund(
            String fund,
            String openingBalance,
            String inflows,
            String outflows,
            String closingBalance) {
    }

    public record FileInfo(
            String id,
            String category,
            String name,
            String status,
            String message,
            String periodStart,
            String periodEnd,
            int entryCount,
            String uploadedAt) {
    }

    public record Check(
            String code,
            String description,
            boolean ok,
            String detail) {
    }

    /** Result of search_documents. modeUsed: KEYWORD (keyword only) or HYBRID (keyword and meaning). */
    public record DocumentSearchResult(
            String modeUsed,
            int total,
            List<FoundChunk> chunks,
            String warning) {
    }

    /** page is only filled for PDF; location always comes as readable text to cite. */
    public record FoundChunk(
            String document,
            String fileId,
            String category,
            String location,
            Integer page,
            String text,
            String sha256,
            String chunkId) {
    }

    public record LedgerEntry(
            String date,
            String fund,
            String accountCode,
            String accountName,
            String memo,
            String credit,
            String debit,
            String supplier,
            String paymentMethod,
            boolean interFundTransfer,
            String fileId,
            int page) {
    }
}
