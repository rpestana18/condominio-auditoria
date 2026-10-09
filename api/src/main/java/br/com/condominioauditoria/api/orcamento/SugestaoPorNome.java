package br.com.condominioauditoria.api.orcamento;

import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.enums.BudgetLineMark;
import br.com.condominioauditoria.api.service.calculator.BudgetStructure;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sugestão do destino de uma conta do fluxo pelo nome (RF-03.1.5; ADR 0004, Decisão 4). Comparação de texto
 * determinística, sem IA, sem banco e sem relógio: funciona em qualquer modo de IA, inclusive DESLIGADO.
 *
 * <p>O número da conta nunca influencia (RF-03.1.4): a normalização descarta todos os dígitos, dos dois lados. Assim a
 * conta 1621 do fluxo ("MATERIAL HIDRÁULICO") não tem nada em comum com a linha 1.3.23 da PO ("1621 - Interfones").
 *
 * <p>Nota = palavras em comum ÷ palavras distintas dos dois nomes (Jaccard), comparada como fração exata. O nome do
 * fluxo é comparado com a conta da PO e com a descrição da linha; vale a maior. A melhor linha vira sugestão se a nota
 * alcança o mínimo e se nenhuma outra linha empata com ela. A sugestão nunca é confirmada aqui.
 */
public final class SugestaoPorNome {

    /** Fração exata (sem ponto flutuante). */
    public record Nota(int comuns, int total) implements Comparable<Nota> {

        static final Nota ZERO = new Nota(0, 1);

        @Override
        public int compareTo(Nota o) {
            return Long.compare((long) comuns * o.total, (long) o.comuns * total);
        }

        public BigDecimal valor() {
            return BigDecimal.valueOf(comuns).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        }

        boolean alcanca(BigDecimal minimo) {
            return BigDecimal.valueOf(comuns).compareTo(minimo.multiply(BigDecimal.valueOf(total))) >= 0;
        }
    }

    public sealed interface Resultado permits Sugerida, SemSugestao {
        String motivo();
    }

    public record Sugerida(BudgetLine linha, Nota nota, String motivo) implements Resultado {
    }

    public record SemSugestao(String motivo) implements Resultado {
    }

    private final BigDecimal notaMinima;
    private final Set<String> palavrasVazias;
    private final java.util.Map<String, String> abreviacoes;

    public SugestaoPorNome(PropriedadesDepara propriedades) {
        this.notaMinima = propriedades.notaMinima();
        this.palavrasVazias = Set.copyOf(propriedades.palavrasVazias());
        this.abreviacoes = propriedades.abreviacoes();
    }

    /** Linhas que podem receber débito do fundo Condomínio: linhas de despesa (sem fundos 1.9) e sem "rateio à parte". */
    public static List<BudgetLine> candidatas(BudgetStructure estrutura) {
        return estrutura.groupsWithoutFunds().stream().flatMap(g -> g.lines().stream())
                .filter(l -> l.getMark() != BudgetLineMark.RATEIO_A_PARTE).toList();
    }

    /** Maiúsculas, sem acento, sem pontuação e sem dígitos; abreviações expandidas e palavras vazias fora. */
    public List<String> normalizar(String texto) {
        if (texto == null) {
            return List.of();
        }
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT);
        // Tudo o que não é letra (dígitos, pontuação, símbolos) vira separador
        String soLetras = semAcento.replaceAll("[^A-Z]+", " ").trim();
        if (soLetras.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> palavras = new LinkedHashSet<>();
        for (String p : soLetras.split(" ")) {
            String inteira = abreviacoes.getOrDefault(p, p);
            if (!palavrasVazias.contains(inteira)) {
                palavras.add(inteira);
            }
        }
        return List.copyOf(palavras);
    }

    public Resultado sugerir(String nomeFluxo, List<BudgetLine> candidatas) {
        List<String> fluxo = normalizar(nomeFluxo);
        if (fluxo.isEmpty()) {
            return new SemSugestao("nome da conta do fluxo sem palavras para comparar");
        }
        Nota melhor = Nota.ZERO;
        List<BudgetLine> empatadas = new ArrayList<>();
        List<String> nomeDaMelhor = List.of();
        for (BudgetLine linha : candidatas) {
            List<String> conta = normalizar(linha.getAccount());
            List<String> descricao = normalizar(linha.getDescription());
            Nota pelaConta = nota(fluxo, conta);
            Nota pelaDescricao = nota(fluxo, descricao);
            boolean contaGanha = pelaConta.compareTo(pelaDescricao) >= 0;
            Nota n = contaGanha ? pelaConta : pelaDescricao;
            int c = n.compareTo(melhor);
            if (c > 0) {
                melhor = n;
                empatadas.clear();
                empatadas.add(linha);
                nomeDaMelhor = contaGanha ? conta : descricao;
            } else if (c == 0 && n.comuns() > 0) {
                empatadas.add(linha);
            }
        }
        String fluxoTexto = String.join(" ", fluxo);
        if (empatadas.isEmpty()) {
            return new SemSugestao("fluxo: " + fluxoTexto + "; nenhuma linha da PO com palavras em comum");
        }
        if (!melhor.alcanca(notaMinima)) {
            return new SemSugestao("fluxo: " + fluxoTexto + "; melhor linha " + empatadas.getFirst().getEffectiveCode()
                    + " com nota " + exibir(melhor.valor()) + ", abaixo do mínimo " + exibir(notaMinima));
        }
        if (empatadas.size() > 1) {
            return new SemSugestao("fluxo: " + fluxoTexto + "; empate entre as linhas " + empatadas.stream()
                    .map(BudgetLine::getEffectiveCode).collect(Collectors.joining(", ")) + " (nota "
                    + exibir(melhor.valor()) + "): escolha na lista");
        }
        BudgetLine escolhida = empatadas.getFirst();
        Set<String> comuns = new LinkedHashSet<>(fluxo);
        comuns.retainAll(nomeDaMelhor);
        return new Sugerida(escolhida, melhor, "fluxo: " + fluxoTexto + "; PO " + escolhida.getEffectiveCode() + ": "
                + String.join(" ", nomeDaMelhor) + " (em comum: " + String.join(", ", comuns) + "; nota "
                + exibir(melhor.valor()) + ")");
    }

    private static Nota nota(List<String> a, List<String> b) {
        if (b.isEmpty()) {
            return Nota.ZERO;
        }
        Set<String> uniao = new LinkedHashSet<>(a);
        uniao.addAll(b);
        Set<String> comuns = new LinkedHashSet<>(a);
        comuns.retainAll(b);
        return new Nota(comuns.size(), uniao.size());
    }

    private static String exibir(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }
}
