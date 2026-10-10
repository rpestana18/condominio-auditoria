package br.com.condominioauditoria.mcp.tool;

import br.com.condominioauditoria.contratos.consulta.v1.BuscarDocumentosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ConferenciasDoArquivoRequest;
import br.com.condominioauditoria.contratos.consulta.v1.FiltrosDocumentos;
import br.com.condominioauditoria.contratos.consulta.v1.ListarArquivosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ListarCondominiosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ListarLancamentosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.LocalizacaoTrecho;
import br.com.condominioauditoria.contratos.consulta.v1.ResumoFundosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.TrechoDocumento;
import br.com.condominioauditoria.mcp.client.ApiClient;
import com.fasterxml.jackson.annotation.JsonProperty;
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
 * ("1234.56"). The exception is buscar_documentos, which returns document text to cite (ADR 0003, 5.3).
 *
 * The tool names, the parameter names (read by Spring AI from the Java parameters) and the JSON fields of the
 * responses ({@code @JsonProperty}) are the interface the AI sees, so they keep the Portuguese names until phase 2 of
 * ADR 0006.
 */
@Component
public class CondominiumTools {

    private final ApiClient api;

    public CondominiumTools(ApiClient api) {
        this.api = api;
    }

    @McpTool(name = "listar_condominios",
            description = "Lista os condomínios que o usuário pode consultar, com o id usado nas outras ferramentas.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public List<Condominium> listCondominiums(McpTransportContext context) {
        return call(() -> api.query(context)
                .listarCondominios(ListarCondominiosRequest.getDefaultInstance())
                .getCondominiosList().stream()
                .map(c -> new Condominium(c.getId(), c.getNome()))
                .toList());
    }

    @McpTool(name = "resumo_fundos",
            description = "Saldo anterior, entradas, saídas e saldo atual de cada fundo no fluxo de caixa mais recente "
                    + "do condomínio, mais o total e quantas conferências aritméticas falharam.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public FundSummary fundSummary(McpTransportContext context,
            @McpToolParam(description = "Id do condomínio (veja listar_condominios)") String condominioId) {
        return call(() -> {
            var r = api.query(context).resumoFundos(
                    ResumoFundosRequest.newBuilder().setCondominioId(condominioId).build());
            if (!r.getTemDados()) {
                return new FundSummary(false, null, null, null, null, null, null, null, 0, List.of());
            }
            return new FundSummary(true, r.getArquivoNome(), r.getPeriodoInicio(), r.getPeriodoFim(),
                    r.getSaldoAnterior(), r.getEntradas(), r.getSaidas(), r.getSaldoAtual(),
                    r.getConferenciasComFalha(),
                    r.getFundosList().stream().map(f -> new Fund(f.getFundo(), f.getSaldoAnterior(), f.getEntradas(),
                            f.getSaidas(), f.getSaldoAtual())).toList());
        });
    }

    @McpTool(name = "listar_arquivos",
            description = "Arquivos enviados ao sistema, do mais recente para o mais antigo, com o status da leitura.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public List<FileInfo> listFiles(McpTransportContext context,
            @McpToolParam(description = "Id do condomínio") String condominioId,
            @McpToolParam(required = false, description = "Categoria: BALANCETE, EXTRATO, PO, CONTRATO, FOLHA, "
                    + "COMPROVANTE, ATA, CONVENCAO_RI ou OUTROS") String categoria,
            @McpToolParam(required = false, description = "Quantos arquivos no máximo (padrão 50)") Integer limite) {
        return call(() -> api.query(context).listarArquivos(ListarArquivosRequest.newBuilder()
                        .setCondominioId(condominioId)
                        .setCategoria(Objects.toString(categoria, ""))
                        .setLimite(limite == null ? 0 : limite)
                        .build())
                .getArquivosList().stream()
                .map(a -> new FileInfo(a.getId(), a.getCategoria(), a.getNome(), a.getStatus(), a.getMensagem(),
                        a.getPeriodoInicio(), a.getPeriodoFim(), a.getTotalLancamentos(), a.getEnviadoEm()))
                .toList());
    }

    @McpTool(name = "conferencias_do_arquivo",
            description = "Resultado das conferências aritméticas de um arquivo lido (saldo linha a linha, totais por "
                    + "fundo, saldo final e total da posição financeira), com o detalhe quando algo não bate.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public List<Check> fileChecks(McpTransportContext context,
            @McpToolParam(description = "Id do condomínio") String condominioId,
            @McpToolParam(description = "Id do arquivo (veja listar_arquivos)") String arquivoId) {
        return call(() -> api.query(context).conferenciasDoArquivo(ConferenciasDoArquivoRequest.newBuilder()
                        .setCondominioId(condominioId).setArquivoId(arquivoId).build())
                .getConferenciasList().stream()
                .map(c -> new Check(c.getCodigo(), c.getDescricao(), c.getOk(), c.getDetalhe()))
                .toList());
    }

    @McpTool(name = "buscar_lancamentos",
            description = "Lançamentos do fluxo de caixa com filtros por período, fundo e texto (histórico, fornecedor "
                    + "ou conta). Cada lançamento traz o arquivo e a página de origem.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public List<LedgerEntry> findEntries(McpTransportContext context,
            @McpToolParam(description = "Id do condomínio") String condominioId,
            @McpToolParam(required = false, description = "Data inicial, AAAA-MM-DD") String dataInicio,
            @McpToolParam(required = false, description = "Data final, AAAA-MM-DD") String dataFim,
            @McpToolParam(required = false, description = "Parte do nome do fundo") String fundo,
            @McpToolParam(required = false, description = "Parte do histórico, do fornecedor ou da conta") String texto,
            @McpToolParam(required = false, description = "true para só saídas, sem transferências entre fundos")
            Boolean somenteSaidas,
            @McpToolParam(required = false, description = "Quantos lançamentos no máximo (padrão 500, máximo 5000)")
            Integer limite) {
        return call(() -> {
            var request = ListarLancamentosRequest.newBuilder()
                    .setCondominioId(condominioId)
                    .setDataInicio(Objects.toString(dataInicio, ""))
                    .setDataFim(Objects.toString(dataFim, ""))
                    .setFundo(Objects.toString(fundo, ""))
                    .setTexto(Objects.toString(texto, ""))
                    .setSomenteSaidas(Boolean.TRUE.equals(somenteSaidas))
                    .setLimite(limite == null ? 0 : limite)
                    .build();
            List<LedgerEntry> list = new ArrayList<>();
            api.query(context).listarLancamentos(request).forEachRemaining(l -> list.add(new LedgerEntry(
                    l.getData(), l.getFundo(), l.getContaCodigo(), l.getContaNome(), l.getHistorico(), l.getCredito(),
                    l.getDebito(), l.getFornecedor(), l.getMeioPagamento(), l.getTransferenciaEntreFundos(),
                    l.getArquivoId(), l.getPagina())));
            return list;
        });
    }

    @McpTool(name = "buscar_documentos",
            description = "Busca trechos nos documentos enviados do condomínio (atas, contratos, convenção, extratos, "
                    + "balancetes, planilhas e outros) para responder o que está escrito neles. Cada trecho vem com o "
                    + "nome do documento, a categoria e a localização no original (\"página 3\", \"aba Plan1, linhas "
                    + "2–31\" ou \"parágrafos 4–7\"): cite sempre essa localização ao usar o trecho. O texto é "
                    + "transcrição literal e não foi conferido: valores que aparecem nele não servem para somar ou "
                    + "comparar; para números use resumo_fundos, buscar_lancamentos e conferencias_do_arquivo. "
                    + "Sem resultado não prova que algo não existe: só que não foi achado nos documentos indexados. "
                    + "Só aparecem arquivos já indexados (situação da indexação na lista de arquivos).",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public DocumentSearchResult searchDocuments(McpTransportContext context,
            @McpToolParam(description = "Id do condomínio (veja listar_condominios)") String condominioId,
            @McpToolParam(description = "O que procurar, em português. Palavras soltas acham qualquer forma "
                    + "(com ou sem acento); \"frase entre aspas\" exige a frase exata; -palavra exclui trechos com "
                    + "ela. Ex.: \"reajuste da taxa\" -2023") String texto,
            @McpToolParam(required = false, description = "Só estas categorias: BALANCETE, EXTRATO, PO, CONTRATO, "
                    + "FOLHA, COMPROVANTE, ATA, CONVENCAO_RI ou OUTROS. Vazio = todas") List<String> categorias,
            @McpToolParam(required = false, description = "Só documentos com competência a partir desta data, "
                    + "AAAA-MM-DD") String dataInicio,
            @McpToolParam(required = false, description = "Só documentos com competência até esta data, AAAA-MM-DD")
            String dataFim,
            @McpToolParam(required = false, description = "Só estes arquivos (ids de listar_arquivos). Vazio = todos")
            List<String> arquivoIds,
            @McpToolParam(required = false, description = "Quantos trechos no máximo, de 1 a 50 (padrão 10)")
            Integer limite) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("Informe o texto da busca");
        }
        if (limite != null && limite < 1) {
            throw new IllegalArgumentException("O limite vai de 1 a 50; recebido " + limite);
        }
        var filters = FiltrosDocumentos.newBuilder()
                .addAllCategorias(withoutBlanks(categorias))
                .setDataInicio(Objects.toString(dataInicio, "").strip())
                .setDataFim(Objects.toString(dataFim, "").strip())
                .addAllArquivoIds(withoutBlanks(arquivoIds))
                .build();
        var request = BuscarDocumentosRequest.newBuilder()
                .setCondominioId(Objects.toString(condominioId, "").strip())
                .setTexto(texto.strip())
                .setFiltros(filters)
                .setLimite(limite == null ? DEFAULT_SEARCH_LIMIT : Math.min(limite, MAX_SEARCH_LIMIT))
                .build();
        return call(() -> {
            var r = api.query(context).buscarDocumentos(request);
            String mode = switch (r.getModoUsado()) {
                case MODO_BUSCA_DOCUMENTOS_PALAVRA -> "PALAVRA";
                case MODO_BUSCA_DOCUMENTOS_HIBRIDA -> "HIBRIDA";
                default -> "NAO_INFORMADO";
            };
            List<FoundChunk> chunks = r.getTrechosList().stream().map(CondominiumTools::chunk).toList();
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

    public static FoundChunk chunk(TrechoDocumento t) {
        Integer page = t.getLocalizacao().hasPagina() ? t.getLocalizacao().getPagina().getPagina() : null;
        return new FoundChunk(t.getNomeArquivo(), t.getArquivoId(), t.getCategoria(),
                readableLocation(t.getLocalizacao()), page, t.getTexto(), t.getSha256(), t.getTrechoId());
    }

    /** "página 3", "aba Plan1, linhas 2–31" or "parágrafos 4–7, seção Cláusula 5". */
    public static String readableLocation(LocalizacaoTrecho l) {
        return switch (l.getTipoCase()) {
            case PAGINA -> "página " + l.getPagina().getPagina();
            case PLANILHA -> "aba " + l.getPlanilha().getAba() + ", "
                    + range("linha", "linhas", l.getPlanilha().getLinhaInicio(), l.getPlanilha().getLinhaFim());
            case PARAGRAFOS -> range("parágrafo", "parágrafos", l.getParagrafos().getParagrafoInicio(),
                    l.getParagrafos().getParagrafoFim())
                    + (l.getParagrafos().getSecao().isBlank() ? "" : ", seção " + l.getParagrafos().getSecao());
            case TIPO_NOT_SET -> "localização não informada";
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

    public record Condominium(String id, @JsonProperty("nome") String name) {
    }

    public record FundSummary(
            @JsonProperty("temDados") boolean hasData,
            @JsonProperty("arquivo") String file,
            @JsonProperty("periodoInicio") String periodStart,
            @JsonProperty("periodoFim") String periodEnd,
            @JsonProperty("saldoAnterior") String openingBalance,
            @JsonProperty("entradas") String inflows,
            @JsonProperty("saidas") String outflows,
            @JsonProperty("saldoAtual") String closingBalance,
            @JsonProperty("conferenciasComFalha") int failedChecks,
            @JsonProperty("fundos") List<Fund> funds) {
    }

    public record Fund(
            @JsonProperty("fundo") String fund,
            @JsonProperty("saldoAnterior") String openingBalance,
            @JsonProperty("entradas") String inflows,
            @JsonProperty("saidas") String outflows,
            @JsonProperty("saldoAtual") String closingBalance) {
    }

    public record FileInfo(
            String id,
            @JsonProperty("categoria") String category,
            @JsonProperty("nome") String name,
            String status,
            @JsonProperty("mensagem") String message,
            @JsonProperty("periodoInicio") String periodStart,
            @JsonProperty("periodoFim") String periodEnd,
            @JsonProperty("totalLancamentos") int entryCount,
            @JsonProperty("enviadoEm") String uploadedAt) {
    }

    public record Check(
            @JsonProperty("codigo") String code,
            @JsonProperty("descricao") String description,
            boolean ok,
            @JsonProperty("detalhe") String detail) {
    }

    /** Result of buscar_documentos. modoUsado: PALAVRA (keyword only) or HIBRIDA (keyword and meaning). */
    public record DocumentSearchResult(
            @JsonProperty("modoUsado") String modeUsed,
            int total,
            @JsonProperty("trechos") List<FoundChunk> chunks,
            @JsonProperty("aviso") String warning) {
    }

    /** pagina is only filled for PDF; localizacao always comes as readable text to cite. */
    public record FoundChunk(
            @JsonProperty("documento") String document,
            @JsonProperty("arquivoId") String fileId,
            @JsonProperty("categoria") String category,
            @JsonProperty("localizacao") String location,
            @JsonProperty("pagina") Integer page,
            @JsonProperty("texto") String text,
            String sha256,
            @JsonProperty("trechoId") String chunkId) {
    }

    public record LedgerEntry(
            @JsonProperty("data") String date,
            @JsonProperty("fundo") String fund,
            @JsonProperty("contaCodigo") String accountCode,
            @JsonProperty("contaNome") String accountName,
            @JsonProperty("historico") String memo,
            @JsonProperty("credito") String credit,
            @JsonProperty("debito") String debit,
            @JsonProperty("fornecedor") String supplier,
            @JsonProperty("meioPagamento") String paymentMethod,
            @JsonProperty("transferenciaEntreFundos") boolean interFundTransfer,
            @JsonProperty("arquivoId") String fileId,
            @JsonProperty("pagina") int page) {
    }
}
