package br.com.condominioauditoria.rag.dominio.po;

import static br.com.condominioauditoria.rag.dominio.Dinheiro.formatarBr;

import br.com.condominioauditoria.rag.dominio.fluxo.Verificacao;
import br.com.condominioauditoria.rag.dominio.po.PrevisaoOrcamentaria.LinhaPo;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Confere a PO lida contra ela mesma (RF-03.1.2, ADR 0004 Decisão 1). Se algum número foi lido errado, alguma soma
 * não bate. Mesmo formato das conferências do fluxo: código, ok e detalhe legível com as duas somas.
 *
 * <ul>
 *   <li>{@code SUBTOTAL_GRUPO}, uma por grupo: soma das linhas = subtotal impresso.</li>
 *   <li>{@code TOTAL}: soma dos subtotais impressos = total impresso.</li>
 *   <li>{@code PREVISTO_MES}: total − fundos = soma dos subtotais dos demais grupos.</li>
 *   <li>{@code FUNDO_TAXA}: cada fundo = taxa da coluna "%" sobre o previsto do mês (RF-03.1.3).</li>
 *   <li>{@code CODIGO_REPETIDO}: código de linha impresso mais de uma vez.</li>
 * </ul>
 *
 * <p>A comparação é exata, ao centavo, sem tolerância: subtotal impresso arredondado na origem aparece como
 * diferença, com as duas somas, e quem decide é o Admin (RF-03.1.2, Q29).
 *
 * <p>Os grupos são as linhas lidas depois de cada linha GRUPO, na ordem do documento (não pelo prefixo do código,
 * que pode estar repetido ou errado).
 */
public final class ConferenciaPo {

    private static final BigDecimal CEM = new BigDecimal("100");
    private static final Pattern PERCENTUAL = Pattern.compile("^-?\\d+(\\.\\d{3})*,\\d+%$");

    private ConferenciaPo() {
    }

    public static List<Verificacao> conferir(PrevisaoOrcamentaria po) {
        Estrutura e = Estrutura.de(po);
        List<Verificacao> resultado = new ArrayList<>();
        for (Grupo g : e.grupos()) {
            resultado.add(subtotal(g));
        }
        if (!e.semGrupo().isEmpty()) {
            resultado.add(new Verificacao("LINHA_SEM_GRUPO", "Toda linha pertence a um grupo", false,
                    "linhas antes do primeiro grupo: " + e.semGrupo().stream().map(LinhaPo::codigoImpresso)
                            .collect(Collectors.joining(", "))));
        }
        resultado.add(total(e));
        resultado.add(previstoMes(e));
        if (e.total() != null) {
            e.fundos().ifPresent(f -> resultado.add(fundoTaxa(f, e)));
        }
        resultado.add(codigoRepetido(po));
        return resultado;
    }

    /** Previsto do mês como impresso: total − fundos. Vazio sem linha de total ou sem grupo de fundos. */
    public static Optional<BigDecimal> previstoDoMes(PrevisaoOrcamentaria po) {
        Estrutura e = Estrutura.de(po);
        if (e.total() == null || e.fundos().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(e.total().orcado().subtract(e.fundos().get().linha().orcado()));
    }

    private static Verificacao subtotal(Grupo g) {
        LinhaPo grupo = g.linha();
        BigDecimal soma = g.somaDasLinhas();
        BigDecimal impresso = grupo.orcado();
        String descricao = "Soma das linhas do grupo " + grupo.codigoImpresso() + " = subtotal impresso";
        String nome = grupo.codigoImpresso() + " " + grupo.descricao();
        if (soma.compareTo(impresso) == 0) {
            return new Verificacao("SUBTOTAL_GRUPO", descricao, true,
                    "%s: soma das linhas %s; impresso %s".formatted(nome, formatarBr(soma), formatarBr(impresso)));
        }
        return new Verificacao("SUBTOTAL_GRUPO", descricao, false,
                "%s: soma das linhas %s; impresso %s; diferença %s".formatted(nome, formatarBr(soma),
                        formatarBr(impresso), formatarBr(impresso.subtract(soma))));
    }

    private static Verificacao total(Estrutura e) {
        String descricao = "Soma dos grupos = total impresso";
        if (e.total() == null) {
            return new Verificacao("TOTAL", descricao, false, "linha de total (código 1) não encontrada");
        }
        BigDecimal soma = e.grupos().stream().map(g -> g.linha().orcado()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal impresso = e.total().orcado();
        boolean ok = soma.compareTo(impresso) == 0;
        return new Verificacao("TOTAL", descricao, ok, "soma dos grupos %s; impresso %s%s".formatted(formatarBr(soma),
                formatarBr(impresso), ok ? "" : "; diferença " + formatarBr(impresso.subtract(soma))));
    }

    /**
     * Total − fundos, conferido contra a soma dos subtotais impressos dos demais grupos. Quando a soma das linhas
     * desses grupos é outra (arredondamento na origem), o detalhe mostra as duas; a diferença em si já aparece no
     * SUBTOTAL_GRUPO do grupo.
     */
    private static Verificacao previstoMes(Estrutura e) {
        String descricao = "Previsto do mês = total menos os fundos";
        if (e.total() == null || e.fundos().isEmpty()) {
            return new Verificacao("PREVISTO_MES", descricao, false,
                    e.total() == null ? "linha de total não encontrada" : "grupo de fundos não encontrado");
        }
        BigDecimal total = e.total().orcado();
        BigDecimal fundos = e.fundos().get().linha().orcado();
        BigDecimal previsto = total.subtract(fundos);
        BigDecimal subtotais = e.somaDosSubtotaisSemFundos();
        BigDecimal linhas = e.somaDasLinhasSemFundos();
        boolean ok = previsto.compareTo(subtotais) == 0;
        String detalhe = "%s - %s = %s".formatted(formatarBr(total), formatarBr(fundos), formatarBr(previsto));
        if (!ok) {
            detalhe += "; soma dos subtotais dos demais grupos " + formatarBr(subtotais);
        }
        if (linhas.compareTo(previsto) != 0) {
            detalhe += "; soma das linhas dos demais grupos " + formatarBr(linhas);
        }
        return new Verificacao("PREVISTO_MES", descricao, ok, detalhe);
    }

    /** RF-03.1.3: cada fundo é a taxa impressa na coluna "%" sobre o previsto do mês (total − fundos). */
    private static Verificacao fundoTaxa(Grupo fundos, Estrutura e) {
        BigDecimal previsto = e.total().orcado().subtract(fundos.linha().orcado());
        List<String> partes = new ArrayList<>();
        boolean ok = !fundos.linhas().isEmpty();
        for (LinhaPo l : fundos.linhas()) {
            Optional<BigDecimal> taxa = taxa(l.percentualTexto());
            if (taxa.isEmpty()) {
                ok = false;
                partes.add("%s %s: sem taxa na coluna %%".formatted(l.codigoImpresso(), l.descricao()));
                continue;
            }
            BigDecimal esperado = aplicar(taxa.get(), previsto);
            boolean bate = esperado.compareTo(l.orcado()) == 0;
            ok &= bate;
            partes.add("%s %s: %s%% de %s = %s; impresso %s".formatted(l.codigoImpresso(), l.descricao(),
                    formatarBr(taxa.get()), formatarBr(previsto), formatarBr(esperado), formatarBr(l.orcado())));
        }
        return new Verificacao("FUNDO_TAXA", "Cada fundo = taxa da coluna % sobre o previsto do mês", ok,
                partes.isEmpty() ? "grupo de fundos sem linhas" : String.join("; ", partes));
    }

    private static Verificacao codigoRepetido(PrevisaoOrcamentaria po) {
        Map<String, List<Integer>> ordens = new LinkedHashMap<>();
        for (LinhaPo l : po.linhas()) {
            ordens.computeIfAbsent(l.codigoImpresso(), c -> new ArrayList<>()).add(l.ordem());
        }
        List<String> repetidos = ordens.entrySet().stream().filter(en -> en.getValue().size() > 1)
                .map(en -> "%s aparece %d vezes (ordens %s)".formatted(en.getKey(), en.getValue().size(),
                        juntarOrdens(en.getValue())))
                .toList();
        return new Verificacao("CODIGO_REPETIDO", "Nenhum código de linha impresso mais de uma vez",
                repetidos.isEmpty(), repetidos.isEmpty() ? "nenhum código repetido" : String.join("; ", repetidos));
    }

    private static String juntarOrdens(List<Integer> ordens) {
        List<String> textos = ordens.stream().map(String::valueOf).toList();
        if (textos.size() == 1) {
            return textos.getFirst();
        }
        return String.join(", ", textos.subList(0, textos.size() - 1)) + " e " + textos.getLast();
    }

    /** taxa% × base, com arredondamento "meio para cima" em centavos. */
    private static BigDecimal aplicar(BigDecimal taxa, BigDecimal base) {
        return taxa.multiply(base).divide(CEM, 2, RoundingMode.HALF_UP);
    }

    /** "3,00%" vira 3.00. Texto fora do formato: vazio. */
    static Optional<BigDecimal> taxa(String texto) {
        if (texto == null || !PERCENTUAL.matcher(texto.trim()).matches()) {
            return Optional.empty();
        }
        String numero = texto.trim().replace("%", "").replace(".", "").replace(",", ".");
        return Optional.of(new BigDecimal(numero));
    }

    private record Grupo(LinhaPo linha, List<LinhaPo> linhas) {
        BigDecimal somaDasLinhas() {
            return linhas.stream().map(LinhaPo::orcado).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    /** Total, grupos (com as linhas que vêm depois de cada um) e o grupo de fundos. */
    private record Estrutura(LinhaPo total, List<Grupo> grupos, List<LinhaPo> semGrupo, Optional<Grupo> fundos) {

        static Estrutura de(PrevisaoOrcamentaria po) {
            LinhaPo total = null;
            List<Grupo> grupos = new ArrayList<>();
            List<LinhaPo> semGrupo = new ArrayList<>();
            Grupo atual = null;
            for (LinhaPo l : po.linhas()) {
                switch (l.tipo()) {
                    case TOTAL -> total = total == null ? l : total;
                    case GRUPO -> {
                        atual = new Grupo(l, new ArrayList<>());
                        grupos.add(atual);
                    }
                    case LINHA -> {
                        if (atual == null) {
                            semGrupo.add(l);
                        } else {
                            atual.linhas().add(l);
                        }
                    }
                }
            }
            Optional<Grupo> fundos = grupos.stream().filter(Estrutura::ehFundos).findFirst();
            return new Estrutura(total, grupos, semGrupo, fundos);
        }

        /** Grupo de fundos: a coluna de conta (ou a descrição) começa por "Fundos", e não por "Subtotal". */
        private static boolean ehFundos(Grupo g) {
            String conta = g.linha().contaTexto() == null ? "" : normalizar(g.linha().contaTexto());
            return conta.startsWith("fundos") || (!conta.startsWith("subtotal")
                    && normalizar(g.linha().descricao()).startsWith("fundos"));
        }

        BigDecimal somaDosSubtotaisSemFundos() {
            return grupos.stream().filter(g -> fundos.filter(f -> f == g).isEmpty()).map(g -> g.linha().orcado())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal somaDasLinhasSemFundos() {
            return grupos.stream().filter(g -> fundos.filter(f -> f == g).isEmpty()).map(Grupo::somaDasLinhas)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    private static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }
}
