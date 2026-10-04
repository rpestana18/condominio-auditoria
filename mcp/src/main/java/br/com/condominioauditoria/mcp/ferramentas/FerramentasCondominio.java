package br.com.condominioauditoria.mcp.ferramentas;

import br.com.condominioauditoria.contratos.consulta.v1.BuscarDocumentosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ConferenciasDoArquivoRequest;
import br.com.condominioauditoria.contratos.consulta.v1.FiltrosDocumentos;
import br.com.condominioauditoria.contratos.consulta.v1.ListarArquivosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ListarCondominiosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ListarLancamentosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.LocalizacaoTrecho;
import br.com.condominioauditoria.contratos.consulta.v1.ResumoFundosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.TrechoDocumento;
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
 * casas ("1234.56"). A exceção é buscar_documentos, que devolve texto dos documentos para citar (ADR 0003, 5.3).
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
    BuscaDocumentos buscarDocumentos(McpTransportContext contexto,
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
        var filtros = FiltrosDocumentos.newBuilder()
                .addAllCategorias(semVazios(categorias))
                .setDataInicio(Objects.toString(dataInicio, "").strip())
                .setDataFim(Objects.toString(dataFim, "").strip())
                .addAllArquivoIds(semVazios(arquivoIds))
                .build();
        var pedido = BuscarDocumentosRequest.newBuilder()
                .setCondominioId(Objects.toString(condominioId, "").strip())
                .setTexto(texto.strip())
                .setFiltros(filtros)
                .setLimite(limite == null ? LIMITE_PADRAO_BUSCA : Math.min(limite, LIMITE_MAXIMO_BUSCA))
                .build();
        return chamar(() -> {
            var r = backend.consulta(contexto).buscarDocumentos(pedido);
            String modo = switch (r.getModoUsado()) {
                case MODO_BUSCA_DOCUMENTOS_PALAVRA -> "PALAVRA";
                case MODO_BUSCA_DOCUMENTOS_HIBRIDA -> "HIBRIDA";
                default -> "NAO_INFORMADO";
            };
            List<TrechoEncontrado> trechos = r.getTrechosList().stream().map(FerramentasCondominio::trecho).toList();
            return new BuscaDocumentos(modo, trechos.size(), trechos, AVISO_BUSCA);
        });
    }

    private static final int LIMITE_PADRAO_BUSCA = 10;
    private static final int LIMITE_MAXIMO_BUSCA = 50;
    private static final String AVISO_BUSCA = "Texto transcrito dos documentos, não conferido. Cite documento e "
            + "localização. Para valores, use as ferramentas numéricas.";

    private static List<String> semVazios(List<String> valores) {
        return valores == null ? List.of()
                : valores.stream().filter(Objects::nonNull).map(String::strip).filter(v -> !v.isEmpty()).toList();
    }

    static TrechoEncontrado trecho(TrechoDocumento t) {
        Integer pagina = t.getLocalizacao().hasPagina() ? t.getLocalizacao().getPagina().getPagina() : null;
        return new TrechoEncontrado(t.getNomeArquivo(), t.getArquivoId(), t.getCategoria(),
                localizacaoLegivel(t.getLocalizacao()), pagina, t.getTexto(), t.getSha256(), t.getTrechoId());
    }

    /** "página 3", "aba Plan1, linhas 2–31" ou "parágrafos 4–7, seção Cláusula 5". */
    static String localizacaoLegivel(LocalizacaoTrecho l) {
        return switch (l.getTipoCase()) {
            case PAGINA -> "página " + l.getPagina().getPagina();
            case PLANILHA -> "aba " + l.getPlanilha().getAba() + ", "
                    + intervalo("linha", "linhas", l.getPlanilha().getLinhaInicio(), l.getPlanilha().getLinhaFim());
            case PARAGRAFOS -> intervalo("parágrafo", "parágrafos", l.getParagrafos().getParagrafoInicio(),
                    l.getParagrafos().getParagrafoFim())
                    + (l.getParagrafos().getSecao().isBlank() ? "" : ", seção " + l.getParagrafos().getSecao());
            case TIPO_NOT_SET -> "localização não informada";
        };
    }

    private static String intervalo(String singular, String plural, int inicio, int fim) {
        return fim <= inicio ? singular + " " + inicio : plural + " " + inicio + "–" + fim;
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

    /** Resultado de buscar_documentos. modoUsado: PALAVRA (só por palavra) ou HIBRIDA (palavra e sentido). */
    record BuscaDocumentos(String modoUsado, int total, List<TrechoEncontrado> trechos, String aviso) {
    }

    /** pagina só vem preenchida em PDF; localizacao sempre vem em texto legível para citar. */
    record TrechoEncontrado(String documento, String arquivoId, String categoria, String localizacao, Integer pagina,
            String texto, String sha256, String trechoId) {
    }

    record Lancamento(String data, String fundo, String contaCodigo, String contaNome, String historico,
            String credito, String debito, String fornecedor, String meioPagamento, boolean transferenciaEntreFundos,
            String arquivoId, int pagina) {
    }
}
