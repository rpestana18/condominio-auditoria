package br.com.condominioauditoria.rag.leitura.po;

import br.com.condominioauditoria.rag.dominio.po.PrevisaoOrcamentaria;
import br.com.condominioauditoria.rag.dominio.po.PrevisaoOrcamentaria.LinhaPo;
import br.com.condominioauditoria.rag.dominio.po.PrevisaoOrcamentaria.Marca;
import br.com.condominioauditoria.rag.dominio.po.PrevisaoOrcamentaria.TipoLinha;
import br.com.condominioauditoria.rag.leitura.Linha;
import br.com.condominioauditoria.rag.leitura.contrato.DocumentoLido;
import br.com.condominioauditoria.rag.leitura.contrato.DocumentoLido.Pagina;
import br.com.condominioauditoria.rag.leitura.contrato.DocumentoLido.Palavra;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Lê a "PROPOSTA ORÇAMENTÁRIA" (PO) no layout da administradora Protest, usado no piloto (ADR 0004, Decisão 1).
 *
 * <p>Como no fluxo de caixa, a leitura é feita pela posição das palavras da saída do leitor (contrato leitor v1),
 * sem IA. Colunas: Item, Identificação (conta da PO ou marca), Descrição, Orçado anterior, Orçado do exercício, "%"
 * e Observações. Os limites saem do cabeçalho de cada página: valores são alinhados à direita e pertencem à coluna
 * onde terminam ({@code x1}); o início da Descrição é o ponto onde começa o texto de quase todas as linhas, entre o
 * fim do título "IDENTIFICAÇÃO DESPESA" e o título "DESCRIÇÃO" (os títulos são centralizados, por isso não servem
 * de limite direto).
 *
 * <p>A PO é lida como está: o código fica como impresso (o 1.3.2 repetido continua em duas linhas), "%" e Observações
 * ficam como texto. Quem confere as somas é {@link br.com.condominioauditoria.rag.dominio.po.ConferenciaPo}.
 */
public final class InterpretadorPoProtest {

    public static final String SEM_TEXTO = "PO sem texto; OCR ainda não disponível";

    private static final Pattern CODIGO = Pattern.compile("^\\d+(\\.\\d+)*$");
    private static final Pattern EXERCICIO = Pattern.compile("\\d{4}\\s*/\\s*\\d{4}");
    private static final Pattern ROTULO_ORCADO = Pattern.compile("^\\d{4}/\\d{4}$");
    private static final Pattern PERCENTUAL = Pattern.compile("^-?\\d+(\\.\\d{3})*,\\d+%$");
    /** Conta da PO: começa por código numérico, ex.: "1682 - Sindicatura Profissional". */
    private static final Pattern CONTA = Pattern.compile("^\\d{3,}\\s*-.*$");
    /** Distância máxima entre o fim de um valor e o fim do rótulo da coluna. */
    private static final double TOLERANCIA_VALOR = 8;
    /** Folga à esquerda do início da Descrição (o texto começa no mesmo ponto, com variação de décimos). */
    private static final double FOLGA_DESCRICAO = 1.5;

    /**
     * Textos da coluna de conta que são marca, e não conta (RF-03.1.1). Comparados sem acento e sem diferença de
     * maiúsculas, pelo começo do texto ("Sem valor (R$ 0,00)" é "sem valor").
     */
    private static final Map<String, Marca> MARCAS = marcas();
    /** "Rateio à parte" nas Observações também dá a marca (1.4.3 Gás traz outro texto na coluna de conta). */
    private static final String RATEIO_NAS_OBSERVACOES = "rateio a parte";

    private static Map<String, Marca> marcas() {
        Map<String, Marca> m = new LinkedHashMap<>();
        m.put("rateio a parte", Marca.RATEIO_A_PARTE);
        m.put("negociada isencao", Marca.NEGOCIADA_ISENCAO);
        m.put("sem valor", Marca.SEM_VALOR);
        m.put("valor fixo (sem referencia)", Marca.VALOR_FIXO_SEM_REFERENCIA);
        return m;
    }

    /** Reconhece o layout pelo título e pelo cabeçalho ("ORÇADO" e "Observações") da primeira página. */
    public boolean reconhece(DocumentoLido documento) {
        if (!"pdf".equals(documento.tipo()) || documento.paginas().isEmpty()) {
            return false;
        }
        List<Linha> inicio = Linha.agrupar(1, documento.paginas().getFirst().palavras()).stream().limit(10).toList();
        String texto = inicio.stream().map(Linha::texto).collect(Collectors.joining("\n"));
        return texto.contains("PROPOSTA ORÇAMENTÁRIA")
                && inicio.stream().anyMatch(l -> l.contem("ORÇADO"))
                && inicio.stream().anyMatch(l -> l.contem("Observações"));
    }

    /** Nenhuma página com texto: PDF escaneado. */
    public static boolean semTexto(DocumentoLido documento) {
        return "pdf".equals(documento.tipo())
                && documento.paginas().stream().allMatch(p -> p.palavras() == null || p.palavras().isEmpty());
    }

    public PrevisaoOrcamentaria interpretar(DocumentoLido documento) {
        if (semTexto(documento)) {
            throw new LeituraPoException(SEM_TEXTO);
        }
        return new Leitura().ler(documento);
    }

    /** Limites das colunas de uma página. */
    record Colunas(double identificacao, double descricao, double anteriorDireita, double orcadoDireita,
            double percentualDireita, double fimCabecalho, List<String> rotulos) {

        boolean naColuna(Palavra p, double direita) {
            return Math.abs(p.x1() - direita) <= TOLERANCIA_VALOR;
        }
    }

    private static final class Leitura {
        private String titulo;
        private List<String> colunasOrcado;
        private Colunas colunas;
        private final List<LinhaPo> linhas = new ArrayList<>();

        PrevisaoOrcamentaria ler(DocumentoLido documento) {
            for (Pagina pagina : documento.paginas()) {
                List<Linha> doc = Linha.agrupar(pagina.numero(), pagina.palavras());
                if (titulo == null) {
                    doc.stream().filter(l -> l.texto().contains("PROPOSTA ORÇAMENTÁRIA")).findFirst()
                            .ifPresent(l -> titulo = l.texto());
                }
                Colunas daPagina = cabecalho(doc);
                if (daPagina != null) {
                    colunas = daPagina;
                    if (colunasOrcado == null) {
                        colunasOrcado = daPagina.rotulos();
                    }
                }
                if (colunas == null) {
                    throw new LeituraPoException("Cabeçalho da PO não encontrado na pág. " + pagina.numero());
                }
                for (Linha linha : doc) {
                    if (linha.topo() > colunas.fimCabecalho()) {
                        lerLinha(linha);
                    }
                }
            }
            if (titulo == null) {
                throw new LeituraPoException("Título \"PROPOSTA ORÇAMENTÁRIA\" não encontrado");
            }
            if (linhas.isEmpty()) {
                throw new LeituraPoException("PO sem linhas com código");
            }
            Matcher exercicio = EXERCICIO.matcher(titulo);
            return new PrevisaoOrcamentaria(titulo, exercicio.find() ? exercicio.group() : "", colunasOrcado,
                    List.copyOf(linhas));
        }

        /** Cabeçalho em três linhas: "ORÇADO ORÇADO %", "Item IDENTIFICAÇÃO ... Observações" e "2025/2026 2026/2027 Orçado". */
        private static Colunas cabecalho(List<Linha> doc) {
            Linha titulos = doc.stream().filter(l -> l.contem("Item") && l.contem("Observações") && l.contem("DESCRIÇÃO"))
                    .findFirst().orElse(null);
            Linha rotulos = doc.stream()
                    .filter(l -> l.palavras().stream().filter(p -> ROTULO_ORCADO.matcher(p.texto()).matches()).count() == 2)
                    .findFirst().orElse(null);
            if (titulos == null || rotulos == null) {
                return null;
            }
            Palavra identificacao = palavra(titulos, "IDENTIFICAÇÃO");
            Palavra descricao = palavra(titulos, "DESCRIÇÃO");
            List<Palavra> orcados = rotulos.palavras().stream().filter(p -> ROTULO_ORCADO.matcher(p.texto()).matches())
                    .toList();
            Palavra percentual = rotulos.palavras().stream().filter(p -> p.texto().equals("Orçado")).findFirst()
                    .orElseGet(() -> doc.stream().filter(l -> l.contem("%") && l.contem("ORÇADO")).findFirst()
                            .map(l -> palavra(l, "%"))
                            .orElseThrow(() -> new LeituraPoException("Cabeçalho da PO sem a coluna %")));
            double fimTituloConta = titulos.palavras().stream()
                    .filter(p -> p.x0() >= identificacao.x0() && p.x1() < descricao.x0())
                    .mapToDouble(Palavra::x1).max().orElse(identificacao.x1());
            double fimCabecalho = Math.max(titulos.topo(), rotulos.topo());
            double inicioDescricao = inicioDaDescricao(doc, fimCabecalho, fimTituloConta, descricao.x0());
            return new Colunas(identificacao.x0(), inicioDescricao, orcados.get(0).x1(), orcados.get(1).x1(),
                    percentual.x1(), fimCabecalho, orcados.stream().map(Palavra::texto).toList());
        }

        /**
         * Ponto mais frequente onde uma palavra começa entre o fim do título da conta e o título da Descrição: é onde
         * começa o texto da Descrição em todas as linhas. Sem nenhuma, vale o título da Descrição.
         */
        private static double inicioDaDescricao(List<Linha> doc, double fimCabecalho, double de, double ate) {
            Map<Long, Long> frequencia = doc.stream().filter(l -> l.topo() > fimCabecalho)
                    .flatMap(l -> l.palavras().stream())
                    .filter(p -> p.x0() >= de && p.x0() < ate)
                    .collect(Collectors.groupingBy(p -> Math.round(p.x0()), Collectors.counting()));
            return frequencia.entrySet().stream()
                    .max(Map.Entry.<Long, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey((a, b) -> Long.compare(b, a))))
                    .map(e -> (double) e.getKey()).orElse(ate);
        }

        private static Palavra palavra(Linha linha, String texto) {
            return linha.palavras().stream().filter(p -> p.texto().equals(texto)).findFirst()
                    .orElseThrow(() -> new LeituraPoException(
                            "Cabeçalho da PO sem a coluna " + texto + " na pág. " + linha.pagina()));
        }

        private void lerLinha(Linha linha) {
            Palavra primeira = linha.primeira();
            if (!CODIGO.matcher(primeira.texto()).matches() || primeira.x1() >= colunas.identificacao()) {
                boolean temValor = linha.palavras().stream().anyMatch(p -> DinheiroPo.ehValor(p.texto())
                        && (colunas.naColuna(p, colunas.anteriorDireita()) || colunas.naColuna(p, colunas.orcadoDireita())));
                if (temValor) {
                    throw new LeituraPoException("Linha com valor e sem código na pág. " + linha.pagina() + ": "
                            + linha.texto());
                }
                return; // título, rodapé ou texto solto sem valores
            }
            String codigo = primeira.texto();
            List<String> conta = new ArrayList<>();
            List<String> descricao = new ArrayList<>();
            List<String> observacoes = new ArrayList<>();
            BigDecimal anterior = null;
            BigDecimal orcado = null;
            String percentual = null;
            for (Palavra p : separarNaDescricao(linha.palavras().subList(1, linha.palavras().size()))) {
                String t = p.texto();
                if (DinheiroPo.ehValor(t) && colunas.naColuna(p, colunas.anteriorDireita())) {
                    anterior = unico(anterior, DinheiroPo.deTexto(t), codigo, linha);
                } else if (DinheiroPo.ehValor(t) && colunas.naColuna(p, colunas.orcadoDireita())) {
                    orcado = unico(orcado, DinheiroPo.deTexto(t), codigo, linha);
                } else if (p.x0() > colunas.orcadoDireita()) {
                    if (percentual == null && observacoes.isEmpty() && PERCENTUAL.matcher(t).matches()
                            && p.x1() <= colunas.percentualDireita() + TOLERANCIA_VALOR) {
                        percentual = t;
                    } else {
                        observacoes.add(t);
                    }
                } else if (p.x0() >= colunas.descricao() - FOLGA_DESCRICAO) {
                    descricao.add(t);
                } else {
                    conta.add(t);
                }
            }
            if (anterior == null || orcado == null) {
                throw new LeituraPoException("Linha %s sem os dois valores orçados na pág. %d: %s"
                        .formatted(codigo, linha.pagina(), linha.texto()));
            }
            String textoConta = String.join(" ", conta);
            String textoObservacoes = observacoes.isEmpty() ? null : String.join(" ", observacoes);
            Marca marcaDaConta = marca(textoConta);
            Marca marca = marcaDaConta != null ? marcaDaConta
                    : textoObservacoes != null && normalizar(textoObservacoes).contains(RATEIO_NAS_OBSERVACOES)
                            ? Marca.RATEIO_A_PARTE : null;
            String contaPo = CONTA.matcher(textoConta).matches() ? textoConta : null;
            String contaTexto = contaPo == null && marcaDaConta == null && !textoConta.isEmpty() ? textoConta : null;
            linhas.add(new LinhaPo(linhas.size() + 1, linha.pagina(), tipo(codigo, linha), codigo, contaPo, contaTexto,
                    marca, String.join(" ", descricao), anterior, orcado, percentual, textoObservacoes));
        }

        /**
         * O PDF às vezes cola a última palavra da conta com a primeira da Descrição (ex.: 1.8.2,
         * "CORRESPONDENCIAApoio"). A palavra que atravessa o início da Descrição é cortada onde um trecho em
         * maiúsculas encontra uma palavra com inicial maiúscula, no ponto mais perto da posição estimada do corte.
         * Sem esse ponto, a palavra fica inteira na coluna onde começa.
         */
        private List<Palavra> separarNaDescricao(List<Palavra> palavras) {
            double fronteira = colunas.descricao();
            List<Palavra> resultado = new ArrayList<>();
            for (Palavra p : palavras) {
                if (p.x0() < fronteira - FOLGA_DESCRICAO && p.x1() > fronteira + FOLGA_DESCRICAO) {
                    resultado.addAll(separarPalavra(p, fronteira));
                } else {
                    resultado.add(p);
                }
            }
            return resultado;
        }

        private static BigDecimal unico(BigDecimal atual, BigDecimal novo, String codigo, Linha linha) {
            if (atual != null) {
                throw new LeituraPoException("Linha %s com dois valores na mesma coluna na pág. %d: %s"
                        .formatted(codigo, linha.pagina(), linha.texto()));
            }
            return novo;
        }

        private static TipoLinha tipo(String codigo, Linha linha) {
            return switch (codigo.split("\\.").length) {
                case 1 -> TipoLinha.TOTAL;
                case 2 -> TipoLinha.GRUPO;
                case 3 -> TipoLinha.LINHA;
                default -> throw new LeituraPoException("Código %s com mais de três níveis na pág. %d"
                        .formatted(codigo, linha.pagina()));
            };
        }

        private static Marca marca(String textoConta) {
            String normal = normalizar(textoConta);
            return MARCAS.entrySet().stream().filter(e -> normal.startsWith(e.getKey())).map(Map.Entry::getValue)
                    .findFirst().orElse(null);
        }
    }

    static List<Palavra> separarPalavra(Palavra p, double fronteira) {
        String t = p.texto();
        int n = t.length();
        double largura = p.x1() - p.x0();
        double estimado = (fronteira - p.x0()) / largura * n;
        int corte = -1;
        for (int k = 1; k < n - 1; k++) {
            boolean ponto = Character.isUpperCase(t.charAt(k - 1)) && Character.isUpperCase(t.charAt(k))
                    && Character.isLowerCase(t.charAt(k + 1));
            if (ponto && (corte < 0 || Math.abs(k - estimado) < Math.abs(corte - estimado))) {
                corte = k;
            }
        }
        if (corte < 0) {
            return List.of(p);
        }
        double xCorte = p.x0() + largura * corte / n;
        return List.of(new Palavra(t.substring(0, corte), p.x0(), xCorte, p.topo(), p.base()),
                new Palavra(t.substring(corte), Math.max(xCorte, fronteira), p.x1(), p.topo(), p.base()));
    }

    /** Sem acento, minúsculas e espaços simples. */
    static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT)
                .replaceAll("\\s+", " ").trim();
    }

    public static class LeituraPoException extends RuntimeException {
        public LeituraPoException(String mensagem) {
            super(mensagem);
        }
    }
}
