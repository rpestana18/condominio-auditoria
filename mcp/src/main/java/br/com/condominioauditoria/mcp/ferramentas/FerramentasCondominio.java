package br.com.condominioauditoria.mcp.ferramentas;

import br.com.condominioauditoria.contratos.consulta.v1.ConferenciasDoArquivoRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ListarArquivosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ListarCondominiosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ListarLancamentosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ResumoFundosRequest;
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
 * Ferramentas que a IA externa enxerga. Todas são só de leitura e respondem com os números já gravados e conferidos
 * pelo backend, com a origem (arquivo e página) para o usuário conferir. Valores em reais vêm como texto com duas
 * casas ("1234.56").
 */
@Component
class FerramentasCondominio {

    private final ClienteBackend backend;

    FerramentasCondominio(ClienteBackend backend) {
        this.backend = backend;
    }

    @McpTool(name = "listar_condominios",
            description = "Lista os condomínios que o usuário pode consultar, com o id usado nas outras ferramentas.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    List<Condominio> listarCondominios(McpTransportContext contexto) {
        return chamar(() -> backend.consulta(contexto)
                .listarCondominios(ListarCondominiosRequest.getDefaultInstance())
                .getCondominiosList().stream()
                .map(c -> new Condominio(c.getId(), c.getNome()))
                .toList());
    }

    @McpTool(name = "resumo_fundos",
            description = "Saldo anterior, entradas, saídas e saldo atual de cada fundo no fluxo de caixa mais recente "
                    + "do condomínio, mais o total e quantas conferências aritméticas falharam.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    ResumoFundos resumoFundos(McpTransportContext contexto,
            @McpToolParam(description = "Id do condomínio (veja listar_condominios)") String condominioId) {
        return chamar(() -> {
            var r = backend.consulta(contexto).resumoFundos(
                    ResumoFundosRequest.newBuilder().setCondominioId(condominioId).build());
            if (!r.getTemDados()) {
                return new ResumoFundos(false, null, null, null, null, null, null, null, 0, List.of());
            }
            return new ResumoFundos(true, r.getArquivoNome(), r.getPeriodoInicio(), r.getPeriodoFim(),
                    r.getSaldoAnterior(), r.getEntradas(), r.getSaidas(), r.getSaldoAtual(),
                    r.getConferenciasComFalha(),
                    r.getFundosList().stream().map(f -> new Fundo(f.getFundo(), f.getSaldoAnterior(), f.getEntradas(),
                            f.getSaidas(), f.getSaldoAtual())).toList());
        });
    }

    @McpTool(name = "listar_arquivos",
            description = "Arquivos enviados ao sistema, do mais recente para o mais antigo, com o status da leitura.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    List<Arquivo> listarArquivos(McpTransportContext contexto,
            @McpToolParam(description = "Id do condomínio") String condominioId,
            @McpToolParam(required = false, description = "Categoria: BALANCETE, EXTRATO, PO, CONTRATO, FOLHA, "
                    + "COMPROVANTE, ATA, CONVENCAO_RI ou OUTROS") String categoria,
            @McpToolParam(required = false, description = "Quantos arquivos no máximo (padrão 50)") Integer limite) {
        return chamar(() -> backend.consulta(contexto).listarArquivos(ListarArquivosRequest.newBuilder()
                        .setCondominioId(condominioId)
                        .setCategoria(Objects.toString(categoria, ""))
                        .setLimite(limite == null ? 0 : limite)
                        .build())
                .getArquivosList().stream()
                .map(a -> new Arquivo(a.getId(), a.getCategoria(), a.getNome(), a.getStatus(), a.getMensagem(),
                        a.getPeriodoInicio(), a.getPeriodoFim(), a.getTotalLancamentos(), a.getEnviadoEm()))
                .toList());
    }

    @McpTool(name = "conferencias_do_arquivo",
            description = "Resultado das conferências aritméticas de um arquivo lido (saldo linha a linha, totais por "
                    + "fundo, saldo final e total da posição financeira), com o detalhe quando algo não bate.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    List<Conferencia> conferenciasDoArquivo(McpTransportContext contexto,
            @McpToolParam(description = "Id do condomínio") String condominioId,
            @McpToolParam(description = "Id do arquivo (veja listar_arquivos)") String arquivoId) {
        return chamar(() -> backend.consulta(contexto).conferenciasDoArquivo(ConferenciasDoArquivoRequest.newBuilder()
                        .setCondominioId(condominioId).setArquivoId(arquivoId).build())
                .getConferenciasList().stream()
                .map(c -> new Conferencia(c.getCodigo(), c.getDescricao(), c.getOk(), c.getDetalhe()))
                .toList());
    }

    @McpTool(name = "buscar_lancamentos",
            description = "Lançamentos do fluxo de caixa com filtros por período, fundo e texto (histórico, fornecedor "
                    + "ou conta). Cada lançamento traz o arquivo e a página de origem.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    List<Lancamento> buscarLancamentos(McpTransportContext contexto,
            @McpToolParam(description = "Id do condomínio") String condominioId,
            @McpToolParam(required = false, description = "Data inicial, AAAA-MM-DD") String dataInicio,
            @McpToolParam(required = false, description = "Data final, AAAA-MM-DD") String dataFim,
            @McpToolParam(required = false, description = "Parte do nome do fundo") String fundo,
            @McpToolParam(required = false, description = "Parte do histórico, do fornecedor ou da conta") String texto,
            @McpToolParam(required = false, description = "true para só saídas, sem transferências entre fundos")
            Boolean somenteSaidas,
            @McpToolParam(required = false, description = "Quantos lançamentos no máximo (padrão 500, máximo 5000)")
            Integer limite) {
        return chamar(() -> {
            var pedido = ListarLancamentosRequest.newBuilder()
                    .setCondominioId(condominioId)
                    .setDataInicio(Objects.toString(dataInicio, ""))
                    .setDataFim(Objects.toString(dataFim, ""))
                    .setFundo(Objects.toString(fundo, ""))
                    .setTexto(Objects.toString(texto, ""))
                    .setSomenteSaidas(Boolean.TRUE.equals(somenteSaidas))
                    .setLimite(limite == null ? 0 : limite)
                    .build();
            List<Lancamento> lista = new ArrayList<>();
            backend.consulta(contexto).listarLancamentos(pedido).forEachRemaining(l -> lista.add(new Lancamento(
                    l.getData(), l.getFundo(), l.getContaCodigo(), l.getContaNome(), l.getHistorico(), l.getCredito(),
                    l.getDebito(), l.getFornecedor(), l.getMeioPagamento(), l.getTransferenciaEntreFundos(),
                    l.getArquivoId(), l.getPagina())));
            return lista;
        });
    }

    private static <T> T chamar(Supplier<T> chamada) {
        try {
            return chamada.get();
        } catch (StatusRuntimeException erro) {
            throw ClienteBackend.traduzir(erro);
        }
    }

    // ---- respostas (viram JSON para a IA) ----

    record Condominio(String id, String nome) {
    }

    record ResumoFundos(boolean temDados, String arquivo, String periodoInicio, String periodoFim, String saldoAnterior,
            String entradas, String saidas, String saldoAtual, int conferenciasComFalha, List<Fundo> fundos) {
    }

    record Fundo(String fundo, String saldoAnterior, String entradas, String saidas, String saldoAtual) {
    }

    record Arquivo(String id, String categoria, String nome, String status, String mensagem, String periodoInicio,
            String periodoFim, int totalLancamentos, String enviadoEm) {
    }

    record Conferencia(String codigo, String descricao, boolean ok, String detalhe) {
    }

    record Lancamento(String data, String fundo, String contaCodigo, String contaNome, String historico,
            String credito, String debito, String fornecedor, String meioPagamento, boolean transferenciaEntreFundos,
            String arquivoId, int pagina) {
    }
}
