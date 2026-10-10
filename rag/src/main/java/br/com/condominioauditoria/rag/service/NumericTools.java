package br.com.condominioauditoria.rag.service;

import br.com.condominioauditoria.contracts.query.v2.FileChecksRequest;
import br.com.condominioauditoria.contracts.query.v2.QueryGrpc;
import br.com.condominioauditoria.contracts.query.v2.Entry;
import br.com.condominioauditoria.contracts.query.v2.ListFilesRequest;
import br.com.condominioauditoria.contracts.query.v2.ListEntriesRequest;
import br.com.condominioauditoria.contracts.query.v2.FundSummaryRequest;
import br.com.condominioauditoria.rag.client.ModelContract.ToolDefinition;
import br.com.condominioauditoria.rag.dto.QueriedData;
import br.com.condominioauditoria.rag.dto.QueriedData.Param;
import br.com.condominioauditoria.rag.dto.QueriedData.Row;
import br.com.condominioauditoria.rag.util.ReaisFormatter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The chat's four numeric tools (ADR 0003, Decision 1 and RF-04.13). Every calculation comes from the api through the
 * {@code query/v2} gRPC; the model never calculates nor transcribes a number of stored data. The result comes back
 * in {@link QueriedData}, already formatted in reais from the contract's exact decimal text.
 */
@Component
public class NumericTools {

    public static final String FUND_SUMMARY = "resumo_fundos";
    public static final String FIND_ENTRIES = "buscar_lancamentos";
    public static final String LIST_FILES = "listar_arquivos";
    public static final String FILE_CHECKS = "conferencias_do_arquivo";

    private static final int DEFAULT_ENTRY_LIMIT = 100;
    private static final int MAX_ENTRY_LIMIT = 500;

    /** Tool requested with a name that does not exist. */
    public static class UnknownToolException extends RuntimeException {
        public UnknownToolException(String name) {
            super("ferramenta desconhecida: " + name);
        }
    }

    public List<ToolDefinition> definitions() {
        return List.of(
                new ToolDefinition(FUND_SUMMARY,
                        "Saldos por fundo no fluxo de caixa mais recente já lido e conferido deste condomínio: "
                                + "saldo anterior, entradas, saídas e saldo atual. Use para \"quanto tem\", "
                                + "\"qual o saldo\" e comparações entre fundos.",
                        Map.of("properties", Map.of(), "required", List.of())),
                new ToolDefinition(FIND_ENTRIES,
                        "Lançamentos gravados, com filtros. Use para \"quanto foi gasto\", \"total\", \"média\" e "
                                + "para listar despesas de um período, fundo ou fornecedor. O sistema calcula os "
                                + "totais; você não precisa somar nada.",
                        Map.of("properties", Map.of(
                                "dataInicio", Map.of("type", "string",
                                        "description", "Data inicial em AAAA-MM-DD. Opcional."),
                                "dataFim", Map.of("type", "string",
                                        "description", "Data final em AAAA-MM-DD. Opcional."),
                                "fundo", Map.of("type", "string",
                                        "description", "Parte do nome do fundo. Opcional."),
                                "texto", Map.of("type", "string",
                                        "description", "Parte do histórico, do fornecedor ou da conta. Opcional."),
                                "somenteSaidas", Map.of("type", "boolean",
                                        "description", "Só saídas (débitos), sem transferência entre fundos."),
                                "limite", Map.of("type", "integer",
                                        "description", "Quantos lançamentos trazer; padrão 100, máximo 500.")),
                                "required", List.of())),
                new ToolDefinition(LIST_FILES,
                        "Arquivos enviados do condomínio, do mais recente para o mais antigo, com categoria, "
                                + "período e estado da leitura. Use para saber o que existe ou o que falta enviar.",
                        Map.of("properties", Map.of(
                                "categoria", Map.of("type", "string",
                                        "description", "TRIAL_BALANCE, BANK_STATEMENT, PO, CONTRACT, PAYROLL, RECEIPT, MINUTES, "
                                                + "BYLAWS ou OTHER. Opcional."),
                                "limite", Map.of("type", "integer", "description", "Padrão 50.")),
                                "required", List.of())),
                new ToolDefinition(FILE_CHECKS,
                        "Conferências aritméticas de um arquivo já lido (o que fechou e o que não fechou). Use "
                                + "quando a pergunta é sobre divergência ou confiabilidade de um documento.",
                        Map.of("properties", Map.of(
                                "arquivoId", Map.of("type", "string",
                                        "description", "Identificador do arquivo (vem de listar_arquivos).")),
                                "required", List.of("arquivoId"))));
    }

    /**
     * Runs the tool in the api and builds the block. {@link io.grpc.StatusRuntimeException} goes up for the caller to
     * decide (permission error becomes a tool result with error; api down becomes UNAVAILABLE).
     */
    public QueriedData execute(String callId, String name, Map<String, Object> arguments,
            String condominiumId, QueryGrpc.QueryBlockingStub api) {
        return switch (name) {
            case FUND_SUMMARY -> fundSummary(callId, condominiumId, api);
            case FIND_ENTRIES -> findEntries(callId, condominiumId, arguments, api);
            case LIST_FILES -> listFiles(callId, condominiumId, arguments, api);
            case FILE_CHECKS -> fileChecks(callId, condominiumId, arguments, api);
            default -> throw new UnknownToolException(name);
        };
    }

    private static QueriedData fundSummary(String callId, String condominiumId,
            QueryGrpc.QueryBlockingStub api) {
        var response = api.fundSummary(FundSummaryRequest.newBuilder().setCondominiumId(condominiumId).build());
        List<Row> lines = new ArrayList<>();
        if (!response.getHasData()) {
            lines.add(new Row("Fluxo de caixa", "nenhum arquivo lido ainda"));
            return new QueriedData(callId, FUND_SUMMARY, List.of(), lines);
        }
        lines.add(new Row("Arquivo", response.getFileName()));
        lines.add(new Row("Período", response.getPeriodStart() + " a " + response.getPeriodEnd()));
        lines.add(new Row("Saldo anterior", ReaisFormatter.format(response.getOpeningBalance())));
        lines.add(new Row("Entradas", ReaisFormatter.format(response.getInflows())));
        lines.add(new Row("Saídas", ReaisFormatter.format(response.getOutflows())));
        lines.add(new Row("Saldo atual", ReaisFormatter.format(response.getClosingBalance())));
        lines.add(new Row("Conferências com falha", String.valueOf(response.getFailedChecks())));
        response.getFundsList().forEach(f -> lines.add(new Row("Fundo " + f.getFund(),
                "saldo anterior " + ReaisFormatter.format(f.getOpeningBalance())
                        + "; entradas " + ReaisFormatter.format(f.getInflows())
                        + "; saídas " + ReaisFormatter.format(f.getOutflows())
                        + "; saldo atual " + ReaisFormatter.format(f.getClosingBalance()))));
        return new QueriedData(callId, FUND_SUMMARY, List.of(), List.copyOf(lines));
    }

    private static QueriedData findEntries(String callId, String condominiumId,
            Map<String, Object> arguments, QueryGrpc.QueryBlockingStub api) {
        String dateFrom = text(arguments.get("dataInicio"));
        String dateTo = text(arguments.get("dataFim"));
        String fund = text(arguments.get("fundo"));
        String search = text(arguments.get("texto"));
        boolean outflowsOnly = Boolean.TRUE.equals(arguments.get("somenteSaidas"))
                || "true".equalsIgnoreCase(text(arguments.get("somenteSaidas")));
        int limit = integer(arguments.get("limite"), DEFAULT_ENTRY_LIMIT);
        limit = Math.min(Math.max(1, limit), MAX_ENTRY_LIMIT);

        List<Param> params = new ArrayList<>();
        append(params, "dataInicio", dateFrom);
        append(params, "dataFim", dateTo);
        append(params, "fundo", fund);
        append(params, "texto", search);
        if (outflowsOnly) {
            params.add(new Param("somenteSaidas", "sim"));
        }
        params.add(new Param("limite", String.valueOf(limit)));

        var request = ListEntriesRequest.newBuilder().setCondominiumId(condominiumId)
                .setDateFrom(dateFrom).setDateTo(dateTo).setFund(fund).setText(search)
                .setOutflowsOnly(outflowsOnly).setLimit(limit).build();
        Iterator<Entry> stream = api.listEntries(request);

        List<Row> items = new ArrayList<>();
        BigDecimal credits = BigDecimal.ZERO.setScale(2);
        BigDecimal debits = BigDecimal.ZERO.setScale(2);
        int count = 0;
        while (stream.hasNext()) {
            Entry l = stream.next();
            count++;
            BigDecimal credit = ReaisFormatter.parse(l.getCredit());
            BigDecimal debit = ReaisFormatter.parse(l.getDebit());
            credits = credits.add(credit);
            debits = debits.add(debit);
            String value = debit.signum() != 0 ? ReaisFormatter.format(debit) + " (saída)"
                    : ReaisFormatter.format(credit) + " (entrada)";
            items.add(new Row(l.getDate() + " — " + description(l), value));
        }
        List<Row> lines = new ArrayList<>();
        lines.add(new Row("Lançamentos encontrados", String.valueOf(count)));
        lines.add(new Row("Total das entradas", ReaisFormatter.format(credits)));
        lines.add(new Row("Total das saídas", ReaisFormatter.format(debits)));
        if (count > 0) {
            lines.add(new Row("Média das saídas por lançamento",
                    ReaisFormatter.format(debits.divide(BigDecimal.valueOf(count), 2,
                            java.math.RoundingMode.HALF_UP))));
        }
        lines.addAll(items);
        return new QueriedData(callId, FIND_ENTRIES, List.copyOf(params), List.copyOf(lines));
    }

    private static String description(Entry l) {
        StringBuilder text = new StringBuilder(l.getFund());
        if (!l.getMemo().isBlank()) {
            text.append(" — ").append(l.getMemo());
        }
        if (!l.getSupplier().isBlank()) {
            text.append(" — ").append(l.getSupplier());
        }
        return text.toString();
    }

    private static QueriedData listFiles(String callId, String condominiumId,
            Map<String, Object> arguments, QueryGrpc.QueryBlockingStub api) {
        String category = text(arguments.get("categoria"));
        int limit = integer(arguments.get("limite"), 0);
        List<Param> params = new ArrayList<>();
        append(params, "categoria", category);
        if (limit > 0) {
            params.add(new Param("limite", String.valueOf(limit)));
        }
        var response = api.listFiles(ListFilesRequest.newBuilder().setCondominiumId(condominiumId)
                .setCategory(category).setLimit(limit).build());
        List<Row> lines = new ArrayList<>();
        lines.add(new Row("Arquivos encontrados", String.valueOf(response.getFilesCount())));
        response.getFilesList().forEach(a -> {
            StringBuilder value = new StringBuilder(a.getCategory()).append("; ").append(a.getStatus());
            if (!a.getPeriodStart().isBlank()) {
                value.append("; período ").append(a.getPeriodStart()).append(" a ").append(a.getPeriodEnd());
            }
            value.append("; arquivoId ").append(a.getId());
            lines.add(new Row(a.getName(), value.toString()));
        });
        return new QueriedData(callId, LIST_FILES, List.copyOf(params), List.copyOf(lines));
    }

    private static QueriedData fileChecks(String callId, String condominiumId,
            Map<String, Object> arguments, QueryGrpc.QueryBlockingStub api) {
        String fileId = text(arguments.get("arquivoId"));
        var response = api.fileChecks(FileChecksRequest.newBuilder()
                .setCondominiumId(condominiumId).setFileId(fileId).build());
        List<Row> lines = new ArrayList<>();
        long failures = response.getChecksList().stream().filter(c -> !c.getOk()).count();
        lines.add(new Row("Conferências", response.getChecksCount() + " ao todo, " + failures
                + " sem fechar"));
        response.getChecksList().forEach(c -> lines.add(new Row(c.getCode() + " — " + c.getDescription(),
                (c.getOk() ? "fechou" : "não fechou") + (c.getDetail().isBlank() ? "" : "; " + c.getDetail()))));
        return new QueriedData(callId, FILE_CHECKS,
                List.of(new Param("arquivoId", fileId)), List.copyOf(lines));
    }

    /** Result sent back to the model: the same rows the user will see, plus the callId. */
    public static String forModel(QueriedData dataItem) {
        StringBuilder text = new StringBuilder("chamadaId: ").append(dataItem.callId())
                .append("\nconsulta: ").append(dataItem.query()).append('\n');
        if (!dataItem.params().isEmpty()) {
            text.append("filtros: ");
            dataItem.params().forEach(p -> text.append(p.name()).append('=').append(p.value()).append(' '));
            text.append('\n');
        }
        dataItem.rows().forEach(l -> text.append("- ").append(l.label()).append(": ").append(l.value()).append('\n'));
        text.append("\nEstes números já estão gravados e conferidos. Não os copie na resposta: cite só o "
                + "chamadaId em nosDadosGravados, que o sistema escreve as linhas.\n");
        return text.toString();
    }

    private static void append(List<Param> params, String name, String value) {
        if (value != null && !value.isBlank()) {
            params.add(new Param(name, value));
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static int integer(Object value, int isDefault) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null || text(value).isBlank() ? isDefault : Integer.parseInt(text(value));
        } catch (NumberFormatException error) {
            return isDefault;
        }
    }
}
