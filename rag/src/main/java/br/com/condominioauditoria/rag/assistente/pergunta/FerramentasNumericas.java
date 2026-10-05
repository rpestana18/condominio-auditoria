package br.com.condominioauditoria.rag.assistente.pergunta;

import br.com.condominioauditoria.contratos.consulta.v1.ConferenciasDoArquivoRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ConsultaGrpc;
import br.com.condominioauditoria.contratos.consulta.v1.Lancamento;
import br.com.condominioauditoria.contratos.consulta.v1.ListarArquivosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ListarLancamentosRequest;
import br.com.condominioauditoria.contratos.consulta.v1.ResumoFundosRequest;
import br.com.condominioauditoria.rag.assistente.gateway.ContratoModelo.DefinicaoFerramenta;
import br.com.condominioauditoria.rag.assistente.pergunta.DadoConsultado.Linha;
import br.com.condominioauditoria.rag.assistente.pergunta.DadoConsultado.Parametro;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * As quatro ferramentas numéricas do chat (ADR 0003, Decisão 1 e RF-04.13). Toda conta sai do backend pelo gRPC
 * {@code consulta/v1}; o modelo nunca calcula nem transcreve número de dado gravado. O resultado volta em
 * {@link DadoConsultado}, já formatado em reais a partir do texto decimal exato do contrato.
 */
@Component
public class FerramentasNumericas {

    public static final String RESUMO_FUNDOS = "resumo_fundos";
    public static final String BUSCAR_LANCAMENTOS = "buscar_lancamentos";
    public static final String LISTAR_ARQUIVOS = "listar_arquivos";
    public static final String CONFERENCIAS_DO_ARQUIVO = "conferencias_do_arquivo";

    private static final int LIMITE_LANCAMENTOS_PADRAO = 100;
    private static final int LIMITE_LANCAMENTOS_MAXIMO = 500;

    /** Ferramenta pedida com nome que não existe. */
    public static class FerramentaDesconhecidaException extends RuntimeException {
        public FerramentaDesconhecidaException(String nome) {
            super("ferramenta desconhecida: " + nome);
        }
    }

    public List<DefinicaoFerramenta> definicoes() {
        return List.of(
                new DefinicaoFerramenta(RESUMO_FUNDOS,
                        "Saldos por fundo no fluxo de caixa mais recente já lido e conferido deste condomínio: "
                                + "saldo anterior, entradas, saídas e saldo atual. Use para \"quanto tem\", "
                                + "\"qual o saldo\" e comparações entre fundos.",
                        Map.of("properties", Map.of(), "required", List.of())),
                new DefinicaoFerramenta(BUSCAR_LANCAMENTOS,
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
                new DefinicaoFerramenta(LISTAR_ARQUIVOS,
                        "Arquivos enviados do condomínio, do mais recente para o mais antigo, com categoria, "
                                + "período e estado da leitura. Use para saber o que existe ou o que falta enviar.",
                        Map.of("properties", Map.of(
                                "categoria", Map.of("type", "string",
                                        "description", "BALANCETE, EXTRATO, PO, CONTRATO, FOLHA, COMPROVANTE, ATA, "
                                                + "CONVENCAO_RI ou OUTROS. Opcional."),
                                "limite", Map.of("type", "integer", "description", "Padrão 50.")),
                                "required", List.of())),
                new DefinicaoFerramenta(CONFERENCIAS_DO_ARQUIVO,
                        "Conferências aritméticas de um arquivo já lido (o que fechou e o que não fechou). Use "
                                + "quando a pergunta é sobre divergência ou confiabilidade de um documento.",
                        Map.of("properties", Map.of(
                                "arquivoId", Map.of("type", "string",
                                        "description", "Identificador do arquivo (vem de listar_arquivos).")),
                                "required", List.of("arquivoId"))));
    }

    /**
     * Executa a ferramenta no backend e monta o bloco. {@link io.grpc.StatusRuntimeException} sobe para quem chama
     * decidir (erro de permissão vira resultado de ferramenta com erro; backend fora do ar vira UNAVAILABLE).
     */
    public DadoConsultado executar(String chamadaId, String nome, Map<String, Object> argumentos,
            String condominioId, ConsultaGrpc.ConsultaBlockingStub backend) {
        return switch (nome) {
            case RESUMO_FUNDOS -> resumoFundos(chamadaId, condominioId, backend);
            case BUSCAR_LANCAMENTOS -> buscarLancamentos(chamadaId, condominioId, argumentos, backend);
            case LISTAR_ARQUIVOS -> listarArquivos(chamadaId, condominioId, argumentos, backend);
            case CONFERENCIAS_DO_ARQUIVO -> conferencias(chamadaId, condominioId, argumentos, backend);
            default -> throw new FerramentaDesconhecidaException(nome);
        };
    }

    private static DadoConsultado resumoFundos(String chamadaId, String condominioId,
            ConsultaGrpc.ConsultaBlockingStub backend) {
        var resposta = backend.resumoFundos(ResumoFundosRequest.newBuilder().setCondominioId(condominioId).build());
        List<Linha> linhas = new ArrayList<>();
        if (!resposta.getTemDados()) {
            linhas.add(new Linha("Fluxo de caixa", "nenhum arquivo lido ainda"));
            return new DadoConsultado(chamadaId, RESUMO_FUNDOS, List.of(), linhas);
        }
        linhas.add(new Linha("Arquivo", resposta.getArquivoNome()));
        linhas.add(new Linha("Período", resposta.getPeriodoInicio() + " a " + resposta.getPeriodoFim()));
        linhas.add(new Linha("Saldo anterior", Reais.formatar(resposta.getSaldoAnterior())));
        linhas.add(new Linha("Entradas", Reais.formatar(resposta.getEntradas())));
        linhas.add(new Linha("Saídas", Reais.formatar(resposta.getSaidas())));
        linhas.add(new Linha("Saldo atual", Reais.formatar(resposta.getSaldoAtual())));
        linhas.add(new Linha("Conferências com falha", String.valueOf(resposta.getConferenciasComFalha())));
        resposta.getFundosList().forEach(f -> linhas.add(new Linha("Fundo " + f.getFundo(),
                "saldo anterior " + Reais.formatar(f.getSaldoAnterior())
                        + "; entradas " + Reais.formatar(f.getEntradas())
                        + "; saídas " + Reais.formatar(f.getSaidas())
                        + "; saldo atual " + Reais.formatar(f.getSaldoAtual()))));
        return new DadoConsultado(chamadaId, RESUMO_FUNDOS, List.of(), List.copyOf(linhas));
    }

    private static DadoConsultado buscarLancamentos(String chamadaId, String condominioId,
            Map<String, Object> argumentos, ConsultaGrpc.ConsultaBlockingStub backend) {
        String dataInicio = texto(argumentos.get("dataInicio"));
        String dataFim = texto(argumentos.get("dataFim"));
        String fundo = texto(argumentos.get("fundo"));
        String busca = texto(argumentos.get("texto"));
        boolean somenteSaidas = Boolean.TRUE.equals(argumentos.get("somenteSaidas"))
                || "true".equalsIgnoreCase(texto(argumentos.get("somenteSaidas")));
        int limite = inteiro(argumentos.get("limite"), LIMITE_LANCAMENTOS_PADRAO);
        limite = Math.min(Math.max(1, limite), LIMITE_LANCAMENTOS_MAXIMO);

        List<Parametro> parametros = new ArrayList<>();
        acrescentar(parametros, "dataInicio", dataInicio);
        acrescentar(parametros, "dataFim", dataFim);
        acrescentar(parametros, "fundo", fundo);
        acrescentar(parametros, "texto", busca);
        if (somenteSaidas) {
            parametros.add(new Parametro("somenteSaidas", "sim"));
        }
        parametros.add(new Parametro("limite", String.valueOf(limite)));

        var pedido = ListarLancamentosRequest.newBuilder().setCondominioId(condominioId)
                .setDataInicio(dataInicio).setDataFim(dataFim).setFundo(fundo).setTexto(busca)
                .setSomenteSaidas(somenteSaidas).setLimite(limite).build();
        Iterator<Lancamento> fluxo = backend.listarLancamentos(pedido);

        List<Linha> itens = new ArrayList<>();
        BigDecimal creditos = BigDecimal.ZERO.setScale(2);
        BigDecimal debitos = BigDecimal.ZERO.setScale(2);
        int quantidade = 0;
        while (fluxo.hasNext()) {
            Lancamento l = fluxo.next();
            quantidade++;
            BigDecimal credito = Reais.valor(l.getCredito());
            BigDecimal debito = Reais.valor(l.getDebito());
            creditos = creditos.add(credito);
            debitos = debitos.add(debito);
            String valor = debito.signum() != 0 ? Reais.formatar(debito) + " (saída)"
                    : Reais.formatar(credito) + " (entrada)";
            itens.add(new Linha(l.getData() + " — " + descricao(l), valor));
        }
        List<Linha> linhas = new ArrayList<>();
        linhas.add(new Linha("Lançamentos encontrados", String.valueOf(quantidade)));
        linhas.add(new Linha("Total das entradas", Reais.formatar(creditos)));
        linhas.add(new Linha("Total das saídas", Reais.formatar(debitos)));
        if (quantidade > 0) {
            linhas.add(new Linha("Média das saídas por lançamento",
                    Reais.formatar(debitos.divide(BigDecimal.valueOf(quantidade), 2,
                            java.math.RoundingMode.HALF_UP))));
        }
        linhas.addAll(itens);
        return new DadoConsultado(chamadaId, BUSCAR_LANCAMENTOS, List.copyOf(parametros), List.copyOf(linhas));
    }

    private static String descricao(Lancamento l) {
        StringBuilder texto = new StringBuilder(l.getFundo());
        if (!l.getHistorico().isBlank()) {
            texto.append(" — ").append(l.getHistorico());
        }
        if (!l.getFornecedor().isBlank()) {
            texto.append(" — ").append(l.getFornecedor());
        }
        return texto.toString();
    }

    private static DadoConsultado listarArquivos(String chamadaId, String condominioId,
            Map<String, Object> argumentos, ConsultaGrpc.ConsultaBlockingStub backend) {
        String categoria = texto(argumentos.get("categoria"));
        int limite = inteiro(argumentos.get("limite"), 0);
        List<Parametro> parametros = new ArrayList<>();
        acrescentar(parametros, "categoria", categoria);
        if (limite > 0) {
            parametros.add(new Parametro("limite", String.valueOf(limite)));
        }
        var resposta = backend.listarArquivos(ListarArquivosRequest.newBuilder().setCondominioId(condominioId)
                .setCategoria(categoria).setLimite(limite).build());
        List<Linha> linhas = new ArrayList<>();
        linhas.add(new Linha("Arquivos encontrados", String.valueOf(resposta.getArquivosCount())));
        resposta.getArquivosList().forEach(a -> {
            StringBuilder valor = new StringBuilder(a.getCategoria()).append("; ").append(a.getStatus());
            if (!a.getPeriodoInicio().isBlank()) {
                valor.append("; período ").append(a.getPeriodoInicio()).append(" a ").append(a.getPeriodoFim());
            }
            valor.append("; arquivoId ").append(a.getId());
            linhas.add(new Linha(a.getNome(), valor.toString()));
        });
        return new DadoConsultado(chamadaId, LISTAR_ARQUIVOS, List.copyOf(parametros), List.copyOf(linhas));
    }

    private static DadoConsultado conferencias(String chamadaId, String condominioId,
            Map<String, Object> argumentos, ConsultaGrpc.ConsultaBlockingStub backend) {
        String arquivoId = texto(argumentos.get("arquivoId"));
        var resposta = backend.conferenciasDoArquivo(ConferenciasDoArquivoRequest.newBuilder()
                .setCondominioId(condominioId).setArquivoId(arquivoId).build());
        List<Linha> linhas = new ArrayList<>();
        long falhas = resposta.getConferenciasList().stream().filter(c -> !c.getOk()).count();
        linhas.add(new Linha("Conferências", resposta.getConferenciasCount() + " ao todo, " + falhas
                + " sem fechar"));
        resposta.getConferenciasList().forEach(c -> linhas.add(new Linha(c.getCodigo() + " — " + c.getDescricao(),
                (c.getOk() ? "fechou" : "não fechou") + (c.getDetalhe().isBlank() ? "" : "; " + c.getDetalhe()))));
        return new DadoConsultado(chamadaId, CONFERENCIAS_DO_ARQUIVO,
                List.of(new Parametro("arquivoId", arquivoId)), List.copyOf(linhas));
    }

    /** Resultado mandado de volta ao modelo: as mesmas linhas que o usuário vai ver, mais o chamadaId. */
    public static String paraOModelo(DadoConsultado dado) {
        StringBuilder texto = new StringBuilder("chamadaId: ").append(dado.chamadaId())
                .append("\nconsulta: ").append(dado.consulta()).append('\n');
        if (!dado.parametros().isEmpty()) {
            texto.append("filtros: ");
            dado.parametros().forEach(p -> texto.append(p.nome()).append('=').append(p.valor()).append(' '));
            texto.append('\n');
        }
        dado.linhas().forEach(l -> texto.append("- ").append(l.rotulo()).append(": ").append(l.valor()).append('\n'));
        texto.append("\nEstes números já estão gravados e conferidos. Não os copie na resposta: cite só o "
                + "chamadaId em nosDadosGravados, que o sistema escreve as linhas.\n");
        return texto.toString();
    }

    private static void acrescentar(List<Parametro> parametros, String nome, String valor) {
        if (valor != null && !valor.isBlank()) {
            parametros.add(new Parametro(nome, valor));
        }
    }

    private static String texto(Object valor) {
        return valor == null ? "" : String.valueOf(valor).trim();
    }

    private static int inteiro(Object valor, int padrao) {
        if (valor instanceof Number numero) {
            return numero.intValue();
        }
        try {
            return valor == null || texto(valor).isBlank() ? padrao : Integer.parseInt(texto(valor));
        } catch (NumberFormatException erro) {
            return padrao;
        }
    }
}
