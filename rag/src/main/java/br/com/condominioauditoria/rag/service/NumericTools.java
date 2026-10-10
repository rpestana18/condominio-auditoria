package br.com.condominioauditoria.rag.service;

import br.com.condominioauditoria.contratos.consulta.v1.ConferenciasDoArquivoRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ConsultaGrpc;
import br.com.condominioauditoria.contratos.consulta.v1.Lancamento;
import br.com.condominioauditoria.contratos.consulta.v1.ListarArquivosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ListarLancamentosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ResumoFundosRequest;
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
 * {@code consulta/v1} gRPC; the model never calculates nor transcribes a number of stored data. The result comes back
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
                                        "description", "BALANCETE, EXTRATO, PO, CONTRATO, FOLHA, COMPROVANTE, ATA, "
                                                + "CONVENCAO_RI ou OUTROS. Opcional."),
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
            String condominiumId, ConsultaGrpc.ConsultaBlockingStub api) {
        return switch (name) {
            case FUND_SUMMARY -> fundSummary(callId, condominiumId, api);
            case FIND_ENTRIES -> findEntries(callId, condominiumId, arguments, api);
            case LIST_FILES -> listFiles(callId, condominiumId, arguments, api);
            case FILE_CHECKS -> fileChecks(callId, condominiumId, arguments, api);
            default -> throw new UnknownToolException(name);
        };
    }

    private static QueriedData fundSummary(String callId, String condominiumId,
            ConsultaGrpc.ConsultaBlockingStub api) {
        var response = api.resumoFundos(ResumoFundosRequest.newBuilder().setCondominioId(condominiumId).build());
        List<Row> lines = new ArrayList<>();
        if (!response.getTemDados()) {
            lines.add(new Row("Fluxo de caixa", "nenhum arquivo lido ainda"));
            return new QueriedData(callId, FUND_SUMMARY, List.of(), lines);
        }
        lines.add(new Row("Arquivo", response.getArquivoNome()));
        lines.add(new Row("Período", response.getPeriodoInicio() + " a " + response.getPeriodoFim()));
        lines.add(new Row("Saldo anterior", ReaisFormatter.format(response.getSaldoAnterior())));
        lines.add(new Row("Entradas", ReaisFormatter.format(response.getEntradas())));
        lines.add(new Row("Saídas", ReaisFormatter.format(response.getSaidas())));
        lines.add(new Row("Saldo atual", ReaisFormatter.format(response.getSaldoAtual())));
        lines.add(new Row("Conferências com falha", String.valueOf(response.getConferenciasComFalha())));
        response.getFundosList().forEach(f -> lines.add(new Row("Fundo " + f.getFundo(),
                "saldo anterior " + ReaisFormatter.format(f.getSaldoAnterior())
                        + "; entradas " + ReaisFormatter.format(f.getEntradas())
                        + "; saídas " + ReaisFormatter.format(f.getSaidas())
                        + "; saldo atual " + ReaisFormatter.format(f.getSaldoAtual()))));
        return new QueriedData(callId, FUND_SUMMARY, List.of(), List.copyOf(lines));
    }

    private static QueriedData findEntries(String callId, String condominiumId,
            Map<String, Object> arguments, ConsultaGrpc.ConsultaBlockingStub api) {
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

        var request = ListarLancamentosRequest.newBuilder().setCondominioId(condominiumId)
                .setDataInicio(dateFrom).setDataFim(dateTo).setFundo(fund).setTexto(search)
                .setSomenteSaidas(outflowsOnly).setLimite(limit).build();
        Iterator<Lancamento> stream = api.listarLancamentos(request);

        List<Row> items = new ArrayList<>();
        BigDecimal credits = BigDecimal.ZERO.setScale(2);
        BigDecimal debits = BigDecimal.ZERO.setScale(2);
        int count = 0;
        while (stream.hasNext()) {
            Lancamento l = stream.next();
            count++;
            BigDecimal credit = ReaisFormatter.parse(l.getCredito());
            BigDecimal debit = ReaisFormatter.parse(l.getDebito());
            credits = credits.add(credit);
            debits = debits.add(debit);
            String value = debit.signum() != 0 ? ReaisFormatter.format(debit) + " (saída)"
                    : ReaisFormatter.format(credit) + " (entrada)";
            items.add(new Row(l.getData() + " — " + description(l), value));
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

    private static String description(Lancamento l) {
        StringBuilder text = new StringBuilder(l.getFundo());
        if (!l.getHistorico().isBlank()) {
            text.append(" — ").append(l.getHistorico());
        }
        if (!l.getFornecedor().isBlank()) {
            text.append(" — ").append(l.getFornecedor());
        }
        return text.toString();
    }

    private static QueriedData listFiles(String callId, String condominiumId,
            Map<String, Object> arguments, ConsultaGrpc.ConsultaBlockingStub api) {
        String category = text(arguments.get("categoria"));
        int limit = integer(arguments.get("limite"), 0);
        List<Param> params = new ArrayList<>();
        append(params, "categoria", category);
        if (limit > 0) {
            params.add(new Param("limite", String.valueOf(limit)));
        }
        var response = api.listarArquivos(ListarArquivosRequest.newBuilder().setCondominioId(condominiumId)
                .setCategoria(category).setLimite(limit).build());
        List<Row> lines = new ArrayList<>();
        lines.add(new Row("Arquivos encontrados", String.valueOf(response.getArquivosCount())));
        response.getArquivosList().forEach(a -> {
            StringBuilder value = new StringBuilder(a.getCategoria()).append("; ").append(a.getStatus());
            if (!a.getPeriodoInicio().isBlank()) {
                value.append("; período ").append(a.getPeriodoInicio()).append(" a ").append(a.getPeriodoFim());
            }
            value.append("; arquivoId ").append(a.getId());
            lines.add(new Row(a.getNome(), value.toString()));
        });
        return new QueriedData(callId, LIST_FILES, List.copyOf(params), List.copyOf(lines));
    }

    private static QueriedData fileChecks(String callId, String condominiumId,
            Map<String, Object> arguments, ConsultaGrpc.ConsultaBlockingStub api) {
        String fileId = text(arguments.get("arquivoId"));
        var response = api.conferenciasDoArquivo(ConferenciasDoArquivoRequest.newBuilder()
                .setCondominioId(condominiumId).setArquivoId(fileId).build());
        List<Row> lines = new ArrayList<>();
        long failures = response.getConferenciasList().stream().filter(c -> !c.getOk()).count();
        lines.add(new Row("Conferências", response.getConferenciasCount() + " ao todo, " + failures
                + " sem fechar"));
        response.getConferenciasList().forEach(c -> lines.add(new Row(c.getCodigo() + " — " + c.getDescricao(),
                (c.getOk() ? "fechou" : "não fechou") + (c.getDetalhe().isBlank() ? "" : "; " + c.getDetalhe()))));
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
