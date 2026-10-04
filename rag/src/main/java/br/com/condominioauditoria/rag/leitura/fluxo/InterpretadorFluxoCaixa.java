package br.com.condominioauditoria.rag.leitura.fluxo;

import br.com.condominioauditoria.rag.dominio.Dinheiro;
import br.com.condominioauditoria.rag.dominio.fluxo.FluxoDeCaixa;
import br.com.condominioauditoria.rag.dominio.fluxo.LancamentoFluxo;
import br.com.condominioauditoria.rag.dominio.fluxo.PosicaoFundo;
import br.com.condominioauditoria.rag.dominio.fluxo.SecaoFundo;
import br.com.condominioauditoria.rag.leitura.contrato.DocumentoLido;
import br.com.condominioauditoria.rag.leitura.contrato.DocumentoLido.Pagina;
import br.com.condominioauditoria.rag.leitura.contrato.DocumentoLido.Palavra;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Lê o relatório "FLUXO DE CAIXA" por fundo (layout da administradora Protest, usado no piloto).
 *
 * <p>O relatório tem, para cada fundo: nome, cabeçalho de colunas, SALDO ANTERIOR, lançamentos e TOTAIS.
 * No fim, o quadro POSIÇÃO FINANCEIRA. Conta contábil e histórico ocupam várias linhas; por isso o texto de cada
 * coluna é juntado em blocos (separados por espaço vertical) e cada bloco vai para o lançamento cuja linha de data
 * ele cobre. Isso evita misturar o histórico de um lançamento com o do vizinho.
 */
public final class InterpretadorFluxoCaixa {

    private static final Pattern DATA = Pattern.compile("^\\d{2}/\\d{2}/\\d{4}$");
    private static final Pattern PERIODO = Pattern.compile("PERÍODO DE (\\d{2}/\\d{2}/\\d{4}) À (\\d{2}/\\d{2}/\\d{4})");
    private static final Pattern EMPREENDIMENTO = Pattern.compile("EMPREENDIMENTO: (.+)$");
    private static final Pattern CONTA = Pattern.compile("^(\\d+)\\.\\s*(.*)$");
    private static final DateTimeFormatter DD_MM_AAAA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Margem à esquerda onde fica o nome do fundo e a primeira coluna da Posição Financeira. */
    private static final double MARGEM_NOME = 40;
    /** Espaço vertical que separa o texto de um lançamento do texto do seguinte. */
    private static final double SEPARACAO_BLOCOS = 10;

    /** Reconhece o layout pelo título e pelo cabeçalho da primeira página. */
    public boolean reconhece(DocumentoLido documento) {
        if (!"pdf".equals(documento.tipo()) || documento.paginas().isEmpty()) {
            return false;
        }
        String inicio = Linha.agrupar(1, documento.paginas().getFirst().palavras()).stream()
                .limit(8).map(Linha::texto).collect(Collectors.joining("\n"));
        return inicio.contains("FLUXO DE CAIXA") && inicio.contains("EMPREENDIMENTO:");
    }

    public FluxoDeCaixa interpretar(DocumentoLido documento) {
        return new Leitura().ler(documento);
    }

    /** Estado da leitura, página a página. */
    private static final class Leitura {
        private String empreendimento;
        private LocalDate inicio;
        private LocalDate fim;
        private final List<SecaoFundo> secoes = new ArrayList<>();
        private final List<PosicaoFundo> posicao = new ArrayList<>();
        private PosicaoFundo totalPosicao;

        private String fundo;
        private BigDecimal saldoAnterior;
        private List<LancamentoFluxo> lancamentos;
        private Colunas colunas;
        private final List<Linha> regiao = new ArrayList<>();
        private boolean naPosicaoFinanceira;
        private int ordem;

        FluxoDeCaixa ler(DocumentoLido documento) {
            for (Pagina pagina : documento.paginas()) {
                for (Linha linha : Linha.agrupar(pagina.numero(), pagina.palavras())) {
                    processar(linha);
                }
                fecharRegiao();
            }
            if (fundo != null) {
                throw new LeituraFluxoException("Fundo " + fundo + " sem linha TOTAIS");
            }
            if (inicio == null) {
                throw new LeituraFluxoException("Período do relatório não encontrado");
            }
            return new FluxoDeCaixa(empreendimento, inicio, fim, List.copyOf(secoes), List.copyOf(posicao), totalPosicao);
        }

        private void processar(Linha linha) {
            String texto = linha.texto();
            if (naPosicaoFinanceira) {
                linhaDaPosicao(linha);
                return;
            }
            if (inicio == null) {
                Matcher periodo = PERIODO.matcher(texto);
                if (periodo.find()) {
                    inicio = LocalDate.parse(periodo.group(1), DD_MM_AAAA);
                    fim = LocalDate.parse(periodo.group(2), DD_MM_AAAA);
                    return;
                }
            }
            Matcher emp = EMPREENDIMENTO.matcher(texto);
            if (empreendimento == null && emp.find()) {
                empreendimento = emp.group(1).trim();
                return;
            }
            if (texto.startsWith("POSIÇÃO FINANCEIRA")) {
                fecharRegiao();
                naPosicaoFinanceira = true;
                return;
            }
            var cabecalho = Colunas.doCabecalho(linha);
            if (cabecalho.isPresent()) {
                fecharRegiao();
                colunas = cabecalho.get();
                return;
            }
            if (ehNomeDeFundo(linha)) {
                fecharRegiao();
                if (fundo != null) {
                    throw new LeituraFluxoException("Fundo " + fundo + " terminou sem linha TOTAIS (pág. " + linha.pagina() + ")");
                }
                fundo = texto;
                saldoAnterior = null;
                lancamentos = new ArrayList<>();
                colunas = null;
                return;
            }
            if (fundo == null || colunas == null || ehRestoDoCabecalho(texto)) {
                return; // título do relatório ou "CONTA CONTÁBIL" quebrado em duas linhas
            }
            if (linha.contem("SALDO") && linha.contem("ANTERIOR")) {
                saldoAnterior = valores(linha).getOrDefault(Colunas.Valor.SALDO, Dinheiro.ZERO);
                return;
            }
            if (linha.contem("TOTAIS")) {
                fecharRegiao();
                fecharFundo(linha);
                return;
            }
            regiao.add(linha);
        }

        private static boolean ehRestoDoCabecalho(String texto) {
            return texto.equals("CONTA") || texto.equals("CONTÁBIL") || texto.equals("CONTA CONTÁBIL");
        }

        private boolean ehNomeDeFundo(Linha linha) {
            Palavra primeira = linha.primeira();
            return primeira.x0() < MARGEM_NOME
                    && !DATA.matcher(primeira.texto()).matches()
                    && linha.palavras().stream().noneMatch(p -> Dinheiro.ehValorBr(p.texto()));
        }

        private void fecharFundo(Linha totais) {
            if (saldoAnterior == null) {
                throw new LeituraFluxoException("Fundo " + fundo + " sem SALDO ANTERIOR");
            }
            List<BigDecimal> numeros = totais.palavras().stream()
                    .filter(p -> Dinheiro.ehValorBr(p.texto())).map(p -> Dinheiro.deTextoBr(p.texto())).toList();
            if (numeros.size() != 2) {
                throw new LeituraFluxoException("Linha TOTAIS do fundo " + fundo + " sem crédito e débito: " + totais.texto());
            }
            secoes.add(new SecaoFundo(fundo, saldoAnterior, List.copyOf(lancamentos), numeros.get(0), numeros.get(1)));
            fundo = null;
        }

        /** Junta o texto de várias linhas em blocos e associa cada bloco à linha de data que ele cobre. */
        private void fecharRegiao() {
            if (regiao.isEmpty()) {
                return;
            }
            Map<Colunas.Texto, List<Bloco>> blocos = new EnumMap<>(Colunas.Texto.class);
            for (Colunas.Texto coluna : Colunas.Texto.values()) {
                blocos.put(coluna, blocosDaColuna(coluna));
            }
            for (Linha linha : regiao) {
                if (!DATA.matcher(linha.primeira().texto()).matches()) {
                    continue;
                }
                Map<Colunas.Valor, BigDecimal> valores = valores(linha);
                if (!valores.containsKey(Colunas.Valor.SALDO)) {
                    throw new LeituraFluxoException("Lançamento sem saldo na pág. " + linha.pagina() + ": " + linha.texto());
                }
                String conta = textoNaAltura(blocos.get(Colunas.Texto.CONTA), linha.topo());
                String historico = textoNaAltura(blocos.get(Colunas.Texto.HISTORICO), linha.topo());
                Matcher m = CONTA.matcher(conta);
                String contaCodigo = m.matches() ? m.group(1) : null;
                String contaNome = m.matches() ? m.group(2) : conta;
                BigDecimal credito = valores.getOrDefault(Colunas.Valor.CREDITO, Dinheiro.ZERO);
                BigDecimal debito = valores.getOrDefault(Colunas.Valor.DEBITO, Dinheiro.ZERO);
                lancamentos.add(new LancamentoFluxo(
                        linha.pagina(),
                        ++ordem,
                        LocalDate.parse(linha.primeira().texto(), DD_MM_AAAA),
                        contaCodigo,
                        contaNome,
                        textoNaAltura(blocos.get(Colunas.Texto.CODIGO), linha.topo()),
                        historico,
                        credito,
                        debito,
                        valores.get(Colunas.Valor.SALDO),
                        Enriquecedor.enriquecer(contaNome, historico, credito, debito)));
            }
            regiao.clear();
        }

        private Map<Colunas.Valor, BigDecimal> valores(Linha linha) {
            Map<Colunas.Valor, BigDecimal> valores = new EnumMap<>(Colunas.Valor.class);
            for (Palavra p : linha.palavras()) {
                if (Dinheiro.ehCelulaDeValor(p.texto())) {
                    colunas.colunaDeValor(p).ifPresent(c -> valores.put(c, Dinheiro.deTextoBr(p.texto())));
                }
            }
            return valores;
        }

        private List<Bloco> blocosDaColuna(Colunas.Texto coluna) {
            List<Bloco> blocos = new ArrayList<>();
            Bloco atual = null;
            for (Linha linha : regiao) {
                List<Palavra> palavras = linha.palavras().stream()
                        .filter(p -> !(Dinheiro.ehCelulaDeValor(p.texto()) && colunas.colunaDeValor(p).isPresent()))
                        .filter(p -> !DATA.matcher(p.texto()).matches() || p.x0() > colunas.conta())
                        .filter(p -> colunas.colunaDeTexto(p).filter(coluna::equals).isPresent())
                        .toList();
                if (palavras.isEmpty()) {
                    continue;
                }
                double topo = palavras.getFirst().topo();
                if (atual == null || topo - atual.fim > SEPARACAO_BLOCOS) {
                    atual = new Bloco(topo);
                    blocos.add(atual);
                }
                atual.adicionar(topo, palavras.stream().map(Palavra::texto).collect(Collectors.joining(" ")));
            }
            return blocos;
        }

        private static String textoNaAltura(List<Bloco> blocos, double topo) {
            return blocos.stream().filter(b -> b.inicio - 3 <= topo && topo <= b.fim + 3)
                    .findFirst().map(b -> String.join(" ", b.linhas)).orElse("");
        }

        private void linhaDaPosicao(Linha linha) {
            List<Palavra> numeros = linha.palavras().stream().filter(p -> Dinheiro.ehValorBr(p.texto())).toList();
            if (numeros.size() != 4) {
                return;
            }
            String nome = linha.palavras().stream().filter(p -> !Dinheiro.ehValorBr(p.texto()))
                    .map(Palavra::texto).collect(Collectors.joining(" "));
            PosicaoFundo p = new PosicaoFundo(nome,
                    Dinheiro.deTextoBr(numeros.get(0).texto()), Dinheiro.deTextoBr(numeros.get(1).texto()),
                    Dinheiro.deTextoBr(numeros.get(2).texto()), Dinheiro.deTextoBr(numeros.get(3).texto()));
            if (nome.equals("TOTAL")) {
                totalPosicao = p;
            } else {
                posicao.add(p);
            }
        }
    }

    private static final class Bloco {
        private final double inicio;
        private double fim;
        private final List<String> linhas = new ArrayList<>();

        Bloco(double inicio) {
            this.inicio = inicio;
            this.fim = inicio;
        }

        void adicionar(double topo, String texto) {
            fim = topo;
            linhas.add(texto);
        }
    }

    public static class LeituraFluxoException extends RuntimeException {
        public LeituraFluxoException(String mensagem) {
            super(mensagem);
        }
    }
}
