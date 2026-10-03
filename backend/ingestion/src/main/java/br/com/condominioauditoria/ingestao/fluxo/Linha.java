package br.com.condominioauditoria.ingestao.fluxo;

import br.com.condominioauditoria.ingestao.contrato.DocumentoLido.Palavra;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** Palavras que estão na mesma altura da página, da esquerda para a direita. */
record Linha(int pagina, double topo, List<Palavra> palavras) {

    /** Diferença máxima de altura para duas palavras ficarem na mesma linha. */
    private static final double TOLERANCIA = 2.0;

    static List<Linha> agrupar(int pagina, List<Palavra> palavras) {
        List<Palavra> ordenadas = palavras.stream().sorted(Comparator.comparingDouble(Palavra::topo)).toList();
        List<Linha> linhas = new ArrayList<>();
        List<Palavra> atual = new ArrayList<>();
        double topoAtual = Double.NaN;
        for (Palavra p : ordenadas) {
            if (!atual.isEmpty() && p.topo() - topoAtual > TOLERANCIA) {
                linhas.add(criar(pagina, topoAtual, atual));
                atual = new ArrayList<>();
            }
            if (atual.isEmpty()) {
                topoAtual = p.topo();
            }
            atual.add(p);
        }
        if (!atual.isEmpty()) {
            linhas.add(criar(pagina, topoAtual, atual));
        }
        return linhas;
    }

    private static Linha criar(int pagina, double topo, List<Palavra> palavras) {
        return new Linha(pagina, topo, palavras.stream().sorted(Comparator.comparingDouble(Palavra::x0)).toList());
    }

    String texto() {
        return palavras.stream().map(Palavra::texto).collect(Collectors.joining(" "));
    }

    Palavra primeira() {
        return palavras.getFirst();
    }

    boolean contem(String palavra) {
        return palavras.stream().anyMatch(p -> p.texto().equals(palavra));
    }
}
