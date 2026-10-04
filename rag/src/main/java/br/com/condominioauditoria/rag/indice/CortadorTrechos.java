package br.com.condominioauditoria.rag.indice;

import br.com.condominioauditoria.rag.leitura.contrato.DocumentoLido;
import br.com.condominioauditoria.rag.leitura.contrato.DocumentoLido.Celula;
import br.com.condominioauditoria.rag.leitura.contrato.DocumentoLido.Pagina;
import br.com.condominioauditoria.rag.leitura.contrato.DocumentoLido.Palavra;
import br.com.condominioauditoria.rag.leitura.contrato.DocumentoLido.Paragrafo;
import br.com.condominioauditoria.rag.leitura.contrato.DocumentoLido.Planilha;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Corta o documento lido em trechos pela localização (ADR 0003, Decisão 3), para a citação ser exata:
 * <ul>
 * <li>PDF: por página; página longa vira mais de um trecho, com pequena sobreposição, todos com a mesma página.</li>
 * <li>Excel: por aba, em blocos de até 30 linhas, repetindo a linha de cabeçalho no começo de cada bloco.</li>
 * <li>Word: grupos de parágrafos seguidos até o limite de tamanho.</li>
 * </ul>
 * Nunca atravessa página nem aba. Determinístico: o mesmo documento gera sempre os mesmos trechos. Mudou alguma regra
 * daqui? Suba {@link #VERSAO}: o backend reindexa os arquivos com versão antiga.
 */
public final class CortadorTrechos {

    /** Versão das regras de corte (versaoIndexador no contrato). */
    public static final String VERSAO = "1";

    /** Cerca de 800 tokens em português (aprox. 4 caracteres por token). */
    static final int MAX_CARACTERES = 3200;

    /** Sobreposição entre trechos da mesma página (linhas finais do trecho anterior). */
    static final int SOBREPOSICAO = 200;

    static final int LINHAS_POR_BLOCO = 30;

    /** Diferença máxima de altura (pontos) para duas palavras do PDF ficarem na mesma linha. */
    private static final double TOLERANCIA_LINHA = 2.0;

    private CortadorTrechos() {
    }

    public static DocumentoCortado cortar(DocumentoLido documento) {
        return switch (documento.tipo()) {
            case "pdf" -> cortarPdf(documento.paginas());
            case "xlsx" -> cortarPlanilhas(documento.planilhas());
            case "docx" -> cortarParagrafos(documento.paragrafos());
            default -> throw new IllegalArgumentException("Tipo de documento não suportado: " + documento.tipo());
        };
    }

    // ---------------------------------------------------------------- PDF

    private static DocumentoCortado cortarPdf(List<Pagina> paginas) {
        var trechos = new Trechos();
        for (Pagina pagina : paginas) {
            if ("sem_texto".equals(pagina.metodo()) || pagina.palavras().isEmpty()) {
                continue;
            }
            var local = new Localizacao.Pagina(pagina.numero());
            for (String texto : dividir(linhasDaPagina(pagina.palavras()))) {
                trechos.add(local, texto);
            }
        }
        String motivo = trechos.lista.isEmpty()
                ? paginas.isEmpty() ? "PDF sem páginas"
                        : "PDF sem texto extraível (provavelmente digitalizado); nenhuma das " + paginas.size()
                                + (paginas.size() == 1 ? " página tem texto" : " páginas tem texto")
                : null;
        return new DocumentoCortado(paginas.size(), trechos.lista, motivo);
    }

    /** Agrupa as palavras por altura e devolve o texto de cada linha, de cima para baixo. */
    static List<String> linhasDaPagina(List<Palavra> palavras) {
        List<Palavra> ordenadas = palavras.stream().sorted(Comparator.comparingDouble(Palavra::topo)).toList();
        List<String> linhas = new ArrayList<>();
        List<Palavra> atual = new ArrayList<>();
        double topoAtual = Double.NaN;
        for (Palavra p : ordenadas) {
            if (!atual.isEmpty() && p.topo() - topoAtual > TOLERANCIA_LINHA) {
                linhas.add(textoDaLinha(atual));
                atual = new ArrayList<>();
            }
            if (atual.isEmpty()) {
                topoAtual = p.topo();
            }
            atual.add(p);
        }
        if (!atual.isEmpty()) {
            linhas.add(textoDaLinha(atual));
        }
        return linhas.stream().filter(l -> !l.isBlank()).toList();
    }

    private static String textoDaLinha(List<Palavra> palavras) {
        return palavras.stream().sorted(Comparator.comparingDouble(Palavra::x0)).map(Palavra::texto)
                .collect(Collectors.joining(" ")).strip();
    }

    /**
     * Junta as linhas em textos de até {@link #MAX_CARACTERES}. Cada texto novo começa repetindo as últimas linhas do
     * anterior (até {@link #SOBREPOSICAO} caracteres). Linha maior que o limite é quebrada nas palavras.
     */
    static List<String> dividir(List<String> linhas) {
        List<String> pedacos = new ArrayList<>();
        for (String linha : linhas) {
            pedacos.addAll(quebrarLinhaLonga(linha, MAX_CARACTERES - SOBREPOSICAO - 1));
        }
        List<String> textos = new ArrayList<>();
        List<String> atual = new ArrayList<>();
        int tamanho = 0;
        int sobrepostas = 0; // quantas linhas do começo de "atual" vieram do texto anterior
        for (String pedaco : pedacos) {
            int acrescimo = pedaco.length() + (atual.isEmpty() ? 0 : 1);
            if (atual.size() > sobrepostas && tamanho + acrescimo > MAX_CARACTERES) {
                textos.add(String.join("\n", atual));
                atual = sobreposicao(atual);
                sobrepostas = atual.size();
                tamanho = atual.isEmpty() ? 0 : String.join("\n", atual).length();
                acrescimo = pedaco.length() + (atual.isEmpty() ? 0 : 1);
            }
            atual.add(pedaco);
            tamanho += acrescimo;
        }
        if (atual.size() > sobrepostas) {
            textos.add(String.join("\n", atual));
        }
        return textos;
    }

    private static List<String> sobreposicao(List<String> linhas) {
        List<String> fim = new ArrayList<>();
        int tamanho = 0;
        for (int i = linhas.size() - 1; i >= 0; i--) {
            int novo = tamanho + linhas.get(i).length() + (fim.isEmpty() ? 0 : 1);
            if (novo > SOBREPOSICAO) {
                break;
            }
            fim.addFirst(linhas.get(i));
            tamanho = novo;
        }
        return fim;
    }

    static List<String> quebrarLinhaLonga(String linha, int limite) {
        if (linha.length() <= limite) {
            return List.of(linha);
        }
        List<String> partes = new ArrayList<>();
        var atual = new StringBuilder();
        for (String palavra : linha.split("\\s+")) {
            while (palavra.length() > limite) { // palavra gigante (ex.: sequência sem espaço): corta seco
                if (!atual.isEmpty()) {
                    partes.add(atual.toString());
                    atual.setLength(0);
                }
                partes.add(palavra.substring(0, limite));
                palavra = palavra.substring(limite);
            }
            if (!atual.isEmpty() && atual.length() + 1 + palavra.length() > limite) {
                partes.add(atual.toString());
                atual.setLength(0);
            }
            if (!atual.isEmpty()) {
                atual.append(' ');
            }
            atual.append(palavra);
        }
        if (!atual.isEmpty()) {
            partes.add(atual.toString());
        }
        return partes;
    }

    // ---------------------------------------------------------------- Excel

    private static DocumentoCortado cortarPlanilhas(List<Planilha> planilhas) {
        var trechos = new Trechos();
        for (Planilha planilha : planilhas) {
            Map<Integer, String> linhas = linhasDaPlanilha(planilha.celulas());
            if (linhas.isEmpty()) {
                continue;
            }
            var iterador = linhas.entrySet().iterator();
            var cabecalho = iterador.next();
            if (!iterador.hasNext()) { // aba com uma linha só
                trechos.add(new Localizacao.Planilha(planilha.nome(), cabecalho.getKey(), cabecalho.getKey()),
                        cabecalho.getValue());
                continue;
            }
            List<Map.Entry<Integer, String>> bloco = new ArrayList<>();
            int tamanho = cabecalho.getValue().length();
            while (iterador.hasNext()) {
                var linha = iterador.next();
                if (!bloco.isEmpty() && (bloco.size() == LINHAS_POR_BLOCO
                        || tamanho + 1 + linha.getValue().length() > MAX_CARACTERES)) {
                    fecharBloco(trechos, planilha.nome(), cabecalho.getValue(), bloco);
                    bloco = new ArrayList<>();
                    tamanho = cabecalho.getValue().length();
                }
                bloco.add(linha);
                tamanho += 1 + linha.getValue().length();
            }
            fecharBloco(trechos, planilha.nome(), cabecalho.getValue(), bloco);
        }
        String motivo = trechos.lista.isEmpty() ? "Planilha sem células preenchidas" : null;
        return new DocumentoCortado(planilhas.size(), trechos.lista, motivo);
    }

    /**
     * Texto de cada linha da aba (células em ordem de coluna, separadas por " | "), por número de linha. A primeira
     * linha com conteúdo é tratada como cabeçalho.
     */
    static Map<Integer, String> linhasDaPlanilha(List<Celula> celulas) {
        Map<Integer, List<Celula>> porLinha = new TreeMap<>();
        for (Celula c : celulas) {
            if (c.valor() != null && !c.valor().isBlank()) {
                porLinha.computeIfAbsent(c.linha(), l -> new ArrayList<>()).add(c);
            }
        }
        Map<Integer, String> linhas = new TreeMap<>();
        porLinha.forEach((numero, doLinha) -> linhas.put(numero, doLinha.stream()
                .sorted(Comparator.comparingInt(Celula::coluna)).map(c -> c.valor().strip())
                .collect(Collectors.joining(" | "))));
        return linhas;
    }

    /** O cabeçalho vai no texto de todo bloco; a localização cita só as linhas de dados do bloco. */
    private static void fecharBloco(Trechos trechos, String aba, String cabecalho,
            List<Map.Entry<Integer, String>> bloco) {
        if (bloco.isEmpty()) {
            return;
        }
        String texto = cabecalho + "\n" + bloco.stream().map(Map.Entry::getValue).collect(Collectors.joining("\n"));
        trechos.add(new Localizacao.Planilha(aba, bloco.getFirst().getKey(), bloco.getLast().getKey()), texto);
    }

    // ---------------------------------------------------------------- Word

    private static DocumentoCortado cortarParagrafos(List<Paragrafo> paragrafos) {
        var trechos = new Trechos();
        List<Paragrafo> grupo = new ArrayList<>();
        int tamanho = 0;
        for (Paragrafo p : paragrafos) {
            String texto = p.texto() == null ? "" : p.texto().strip();
            if (texto.isEmpty()) {
                continue;
            }
            if (texto.length() > MAX_CARACTERES) { // parágrafo longo sozinho vira vários trechos com a mesma localização
                fecharGrupo(trechos, grupo);
                grupo = new ArrayList<>();
                tamanho = 0;
                for (String parte : dividir(quebrarLinhaLonga(texto, MAX_CARACTERES - SOBREPOSICAO - 1))) {
                    trechos.add(new Localizacao.Paragrafos(p.ordem(), p.ordem(), ""), parte);
                }
                continue;
            }
            if (!grupo.isEmpty() && tamanho + 1 + texto.length() > MAX_CARACTERES) {
                fecharGrupo(trechos, grupo);
                grupo = new ArrayList<>();
                tamanho = 0;
            }
            grupo.add(new Paragrafo(p.ordem(), texto, p.tabela()));
            tamanho += (tamanho == 0 ? 0 : 1) + texto.length();
        }
        fecharGrupo(trechos, grupo);
        String motivo = trechos.lista.isEmpty() ? "Documento Word sem texto" : null;
        return new DocumentoCortado(1, trechos.lista, motivo);
    }

    private static void fecharGrupo(Trechos trechos, List<Paragrafo> grupo) {
        if (grupo.isEmpty()) {
            return;
        }
        String texto = grupo.stream().map(Paragrafo::texto).collect(Collectors.joining("\n"));
        trechos.add(new Localizacao.Paragrafos(grupo.getFirst().ordem(), grupo.getLast().ordem(), ""), texto);
    }

    /** Numera os trechos na ordem do documento. */
    private static final class Trechos {
        private final List<TrechoCortado> lista = new ArrayList<>();

        void add(Localizacao local, String texto) {
            lista.add(new TrechoCortado(lista.size() + 1, local, texto));
        }
    }
}
