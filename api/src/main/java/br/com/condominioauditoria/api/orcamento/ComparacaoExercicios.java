package br.com.condominioauditoria.api.orcamento;

import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.enums.BudgetLineType;
import br.com.condominioauditoria.api.orcamento.ExercicioDtos.TipoExercicio;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.FundoResultado;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.GrupoResultado;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.LinhaResultado;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.MesExercicio;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.SituacaoFundo;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.SituacaoMes;
import br.com.condominioauditoria.api.service.calculator.BudgetStructure;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Comparação de exercícios (RF-11.6; ADR 0005, Decisão 2): monta as três visões (resumo, por grupo e por rubrica) a
 * partir do resultado do {@link CalculoPrevistoRealizado} de cada exercício, sem acessar banco nem relógio. Nenhum
 * número de um exercício muda: o realizado vem do mesmo cálculo da tela de previsto × realizado.
 *
 * <p>Regras:
 * <ul>
 * <li>exercícios do mais recente para o mais antigo; a variação de cada um é contra o seguinte da lista (o anterior);
 * <li>variação em R$ = atual − anterior; em % só com base diferente de zero (10 casas, exibida com 1 casa, meio para
 * cima); base zero com valor atual = "nova no exercício";
 * <li>previsto do mês = soma das linhas (Q29), sem os fundos; na coluna impressa, a coluna "Orçado anterior";
 * <li>grupos casados pelo código (1.1 a 1.9), sem correspondência; linhas casadas pela rubrica confirmada (RF-11.7);
 * linha sem rubrica confirmada vai para "sem correspondência" e nunca é somada a outra;
 * <li>fundos (1.9) comparam a arrecadação (RF-03.1.9).
 * </ul>
 */
public final class ComparacaoExercicios {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final Locale PT_BR = Locale.of("pt", "BR");

    private ComparacaoExercicios() {
    }

    /** Rubrica confirmada de uma linha. */
    public record RubricaDaLinha(UUID id, String nome, String grupo) {
    }

    /**
     * Um exercício a comparar. {@code linhas}: as da PO (na coluna impressa, as da PO que a imprimiu, com o valor da
     * coluna "Orçado anterior"). {@code acumulado}: o cálculo do acumulado do exercício (nulo na coluna impressa).
     * {@code periodo}: os resultados somados na comparação (o acumulado, ou os meses de "mesmos meses"); vazio = sem
     * realizado. {@code mesesDoPeriodo}: os meses desses resultados (AAAA-MM).
     */
    public record Entrada(String id, TipoExercicio tipo, String rotulo, UUID poId, Integer versao, YearMonth inicio,
            YearMonth fim, List<BudgetLine> linhas, Map<UUID, UUID> fundoPorLinha, Map<UUID, RubricaDaLinha> rubricas,
            PrevistoRealizado acumulado, List<PrevistoRealizado> periodo, List<String> mesesDoPeriodo,
            Integer achadosAbertos) {

        boolean coluna() {
            return tipo == TipoExercicio.COLUNA_IMPRESSA;
        }
    }

    /** {@code fundoId} nulo = todos; o fundo ordinário (Condomínio) = sem os fundos 1.9; outro = só as linhas dele. */
    public record Filtro(UUID fundoId, UUID fundoOrdinarioId, boolean mesmosMeses, String comparando) {
    }

    /** Variação contra o exercício anterior. {@code novaNoExercicio}: base zero e valor atual diferente de zero. */
    public record Variacao(BigDecimal valor, BigDecimal percentual, boolean novaNoExercicio) {
    }

    public record Excesso(String mes, BigDecimal valor, BigDecimal percentual) {
    }

    /** Exercício comparado; {@code periodo} é o período da evidência no previsto × realizado ("acumulado" ou AAAA-MM). */
    public record ExercicioComparado(String id, TipoExercicio tipo, String rotulo, UUID poId, Integer versao,
            String inicio, String fim, String periodo, List<String> meses) {
    }

    /** Visão 1 do RF-11.6. Números nulos quando o exercício não tem o dado (coluna impressa, sem fluxo). */
    public record ResumoExercicio(String exercicioId, BigDecimal previstoMes, BigDecimal previstoExercicio,
            int mesesComFluxo, BigDecimal previsto, BigDecimal realizado, BigDecimal execucao, Excesso maiorExcesso,
            Integer mesesAcimaDoLimite, Integer achadosAbertos, boolean provisorio, Variacao variacaoPrevistoMes,
            Variacao variacaoRealizado, String alvo) {
    }

    public record LinhaUsada(UUID linhaId, String codigo, String descricao, String alvo) {
    }

    /** Valor de um exercício num grupo ou rubrica; {@code alvo} abre a evidência no previsto × realizado. */
    public record ValorComparado(String exercicioId, BigDecimal previstoMes, BigDecimal previsto,
            BigDecimal realizado, Variacao variacaoPrevistoMes, Variacao variacaoRealizado, String alvo,
            List<LinhaUsada> linhas) {
    }

    public record GrupoComparado(String codigo, String descricao, boolean fundos, List<ValorComparado> valores) {
    }

    public record RubricaComparada(UUID rubricaId, String nome, String grupo, List<ValorComparado> valores) {
    }

    /** Linha sem rubrica confirmada: aparece sozinha, com o valor do exercício dela. */
    public record LinhaSemCorrespondencia(String exercicioId, UUID linhaId, String codigo, String conta,
            String descricao, String grupo, BigDecimal previstoMes, BigDecimal previsto, BigDecimal realizado,
            String alvo) {
    }

    public record Resultado(List<ExercicioComparado> exercicios, UUID fundoId, boolean mesmosMeses,
            String comparando, List<ResumoExercicio> resumo, List<GrupoComparado> grupos,
            List<RubricaComparada> linhas, List<LinhaSemCorrespondencia> semCorrespondencia, List<String> avisos) {
    }

    /** Números de uma linha num exercício (previsto do período e realizado nulos sem realizado). */
    private record ValorLinha(BudgetLine linha, String grupo, BigDecimal previstoMes, BigDecimal previsto,
            BigDecimal realizado, String alvo) {
    }

    /** Um exercício já apurado, linha a linha, no escopo do filtro. */
    private record Apurado(Entrada entrada, List<BudgetStructure.Group> grupos, Map<String, List<ValorLinha>> porGrupo,
            boolean comRealizado) {
    }

    public static Resultado comparar(List<Entrada> entradas, Filtro filtro) {
        List<Apurado> apurados = entradas.stream().map(e -> apurar(e, filtro)).toList();
        List<ExercicioComparado> exercicios = entradas.stream().map(e -> new ExercicioComparado(e.id(), e.tipo(),
                e.rotulo(), e.poId(), e.versao(), e.inicio().toString(), e.fim().toString(),
                e.coluna() ? null : periodoDaEvidencia(e), List.copyOf(e.mesesDoPeriodo()))).toList();
        return new Resultado(exercicios, filtro.fundoId(), filtro.mesmosMeses(), filtro.comparando(),
                resumo(apurados, filtro), grupos(apurados), rubricas(apurados), semCorrespondencia(apurados),
                avisos(entradas));
    }

    /**
     * Meses comparados em "mesmos meses": os meses do ano (pelo número) com situação "com fluxo" em todos os
     * exercícios com PO (a coluna impressa não tem realizado e não entra). Meses prorrogados não contam.
     */
    public static Set<Month> mesmosMeses(Collection<PrevistoRealizado> acumulados) {
        Set<Month> comuns = null;
        for (PrevistoRealizado a : acumulados) {
            Set<Month> doExercicio = a == null ? Set.of() : a.meses().stream()
                    .filter(m -> !m.prorrogado() && m.situacao() == SituacaoMes.COM_FLUXO)
                    .map(m -> YearMonth.parse(m.mes()).getMonth()).collect(Collectors.toCollection(TreeSet::new));
            if (comuns == null) {
                comuns = new TreeSet<>(doExercicio);
            } else {
                comuns.retainAll(doExercicio);
            }
        }
        return comuns == null ? Set.of() : Set.copyOf(comuns);
    }

    /** "comparando: setembro", "comparando: agosto e setembro" ou "comparando: nenhum mês com fluxo em todos". */
    public static String comparando(Set<Month> meses) {
        if (meses.isEmpty()) {
            return "comparando: nenhum mês com fluxo em todos os exercícios";
        }
        List<String> nomes = new TreeSet<>(meses).stream().map(m -> m.getDisplayName(TextStyle.FULL, PT_BR)).toList();
        String texto = nomes.size() == 1 ? nomes.getFirst()
                : String.join(", ", nomes.subList(0, nomes.size() - 1)) + " e " + nomes.getLast();
        return "comparando: " + texto;
    }

    /** Variação de {@code atual} contra {@code anterior}; nula se faltar um dos dois. */
    public static Variacao variacao(BigDecimal atual, BigDecimal anterior) {
        if (atual == null || anterior == null) {
            return null;
        }
        BigDecimal valor = atual.subtract(anterior).setScale(2, RoundingMode.HALF_UP);
        if (anterior.signum() == 0) {
            return new Variacao(valor, null, atual.signum() != 0);
        }
        BigDecimal pct = valor.multiply(BigDecimal.valueOf(100)).divide(anterior.abs(), 10, RoundingMode.HALF_UP)
                .setScale(1, RoundingMode.HALF_UP);
        return new Variacao(valor, pct, false);
    }

    private static Apurado apurar(Entrada e, Filtro f) {
        BudgetStructure estrutura = BudgetStructure.of(e.linhas());
        Map<UUID, List<LinhaResultado>> doPeriodo = new HashMap<>();
        Map<UUID, List<FundoResultado>> fundosDoPeriodo = new HashMap<>();
        for (PrevistoRealizado r : e.periodo()) {
            for (GrupoResultado g : nulo(r.grupos())) {
                for (LinhaResultado l : g.linhas()) {
                    doPeriodo.computeIfAbsent(l.linhaId(), k -> new ArrayList<>()).add(l);
                }
            }
            for (FundoResultado fr : nulo(r.fundos())) {
                if (fr.linhaId() != null) {
                    fundosDoPeriodo.computeIfAbsent(fr.linhaId(), k -> new ArrayList<>()).add(fr);
                }
            }
        }
        boolean comRealizado = !e.coluna() && !e.periodo().isEmpty();
        List<BudgetStructure.Group> grupos = new ArrayList<>();
        Map<String, List<ValorLinha>> porGrupo = new LinkedHashMap<>();
        for (BudgetStructure.Group g : estrutura.groups()) {
            if (!grupoNoFiltro(g, f)) {
                continue;
            }
            List<ValorLinha> valores = new ArrayList<>();
            for (BudgetLine l : g.lines()) {
                if (g.funds() && f.fundoId() != null && !f.fundoId().equals(e.fundoPorLinha().get(l.getId()))) {
                    continue;
                }
                BigDecimal previsto = null;
                BigDecimal realizado = null;
                String alvo = null;
                if (comRealizado && g.funds()) {
                    List<FundoResultado> frs = fundosDoPeriodo.getOrDefault(l.getId(), List.of());
                    if (!frs.isEmpty() && frs.stream().allMatch(fr -> fr.situacao() == SituacaoFundo.COMPARADO)) {
                        previsto = soma(frs.stream().map(FundoResultado::previsto).toList());
                        realizado = soma(frs.stream().map(FundoResultado::arrecadado).toList());
                        alvo = CalculoPrevistoRealizado.alvoFundo(frs.getFirst().fundoId());
                    }
                } else if (comRealizado) {
                    List<LinhaResultado> lrs = doPeriodo.getOrDefault(l.getId(), List.of());
                    if (!lrs.isEmpty()) {
                        previsto = soma(lrs.stream().map(LinhaResultado::previsto).toList());
                        realizado = soma(lrs.stream().map(LinhaResultado::realizado).toList());
                    }
                    alvo = CalculoPrevistoRealizado.alvoLinha(l.getId());
                }
                valores.add(new ValorLinha(l, g.line().getEffectiveCode(), valorDaLinha(e, l), previsto, realizado,
                        alvo));
            }
            if (g.funds() && valores.isEmpty()) {
                continue;
            }
            grupos.add(g);
            porGrupo.put(g.line().getEffectiveCode(), valores);
        }
        return new Apurado(e, grupos, porGrupo, comRealizado);
    }

    private static boolean grupoNoFiltro(BudgetStructure.Group g, Filtro f) {
        if (f.fundoId() == null) {
            return true;
        }
        return f.fundoId().equals(f.fundoOrdinarioId()) != g.funds();
    }

    private static boolean filtroDeOutroFundo(Filtro f) {
        return f.fundoId() != null && !f.fundoId().equals(f.fundoOrdinarioId());
    }

    private static List<ResumoExercicio> resumo(List<Apurado> apurados, Filtro f) {
        List<ResumoExercicio> lista = new ArrayList<>();
        BigDecimal[] previstoMes = new BigDecimal[apurados.size()];
        BigDecimal[] realizado = new BigDecimal[apurados.size()];
        for (int i = 0; i < apurados.size(); i++) {
            Apurado a = apurados.get(i);
            List<ValorLinha> linhas = a.porGrupo().entrySet().stream()
                    .filter(x -> filtroDeOutroFundo(f) || !grupoDeFundos(a, x.getKey()))
                    .flatMap(x -> x.getValue().stream()).toList();
            previstoMes[i] = soma(linhas.stream().map(ValorLinha::previstoMes).toList());
            if (a.comRealizado()) {
                if (filtroDeOutroFundo(f)) {
                    realizado[i] = somaOuNulo(linhas.stream().map(ValorLinha::realizado).toList());
                } else {
                    realizado[i] = soma(a.entrada().periodo().stream().map(r -> r.totais().despesaRealizada()).toList());
                }
            }
        }
        for (int i = 0; i < apurados.size(); i++) {
            Apurado a = apurados.get(i);
            Entrada e = a.entrada();
            BigDecimal previsto = null;
            Excesso maior = null;
            Integer acima = null;
            boolean provisorio = false;
            if (a.comRealizado()) {
                if (filtroDeOutroFundo(f)) {
                    previsto = somaOuNulo(a.porGrupo().values().stream().flatMap(List::stream)
                            .map(ValorLinha::previsto).toList());
                } else {
                    previsto = soma(e.periodo().stream().map(r -> r.totais().previsto()).toList());
                    provisorio = e.periodo().stream().anyMatch(PrevistoRealizado::provisorio);
                    List<MesExercicio> meses = e.acumulado() == null ? List.of() : e.acumulado().meses().stream()
                            .filter(m -> !m.prorrogado() && e.mesesDoPeriodo().contains(m.mes())).toList();
                    maior = meses.stream().filter(m -> m.excesso() != null)
                            .max(Comparator.comparing(MesExercicio::excesso))
                            .map(m -> new Excesso(m.mes(), m.excesso(), m.percentualExcesso())).orElse(null);
                    acima = (int) meses.stream().filter(m -> Boolean.TRUE.equals(m.acimaDoLimite())).count();
                }
            }
            int nMeses = (int) (e.fim().getYear() * 12L + e.fim().getMonthValue()
                    - (e.inicio().getYear() * 12L + e.inicio().getMonthValue()) + 1);
            BigDecimal anteriorMes = i + 1 < apurados.size() ? previstoMes[i + 1] : null;
            BigDecimal anteriorRealizado = i + 1 < apurados.size() ? realizado[i + 1] : null;
            lista.add(new ResumoExercicio(e.id(), previstoMes[i],
                    previstoMes[i].multiply(BigDecimal.valueOf(nMeses)).setScale(2, RoundingMode.UNNECESSARY),
                    a.comRealizado() ? e.mesesDoPeriodo().size() : 0, previsto, realizado[i],
                    realizado[i] == null ? null : CalculoPrevistoRealizado.percentual(realizado[i], previsto), maior,
                    acima, e.achadosAbertos(), provisorio, variacao(previstoMes[i], anteriorMes),
                    variacao(realizado[i], anteriorRealizado),
                    a.comRealizado() ? (filtroDeOutroFundo(f) ? CalculoPrevistoRealizado.alvoFundo(f.fundoId())
                            : CalculoPrevistoRealizado.ALVO_TOTAL) : null));
        }
        return List.copyOf(lista);
    }

    private static boolean grupoDeFundos(Apurado a, String codigo) {
        return a.grupos().stream().anyMatch(g -> g.funds() && g.line().getEffectiveCode().equals(codigo));
    }

    private static List<GrupoComparado> grupos(List<Apurado> apurados) {
        // Ordem: a dos grupos no exercício mais recente; os que só existem nos outros vêm depois
        Map<String, BudgetStructure.Group> ordem = new LinkedHashMap<>();
        apurados.forEach(a -> a.grupos().forEach(g -> ordem.putIfAbsent(g.line().getEffectiveCode(), g)));
        List<GrupoComparado> lista = new ArrayList<>();
        for (Map.Entry<String, BudgetStructure.Group> x : ordem.entrySet()) {
            List<ValorComparado> valores = valores(apurados, a -> {
                List<ValorLinha> linhas = a.porGrupo().get(x.getKey());
                if (linhas == null) {
                    return null;
                }
                BudgetStructure.Group g = a.grupos().stream()
                        .filter(gr -> gr.line().getEffectiveCode().equals(x.getKey())).findFirst().orElseThrow();
                String alvo = !a.comRealizado() ? null : g.funds()
                        ? (linhas.size() == 1 ? linhas.getFirst().alvo() : null)
                        : CalculoPrevistoRealizado.alvoGrupo(g.line().getId());
                return new Parcial(linhas, alvo);
            });
            lista.add(new GrupoComparado(x.getKey(), x.getValue().line().getDescription(), x.getValue().funds(),
                    valores));
        }
        return List.copyOf(lista);
    }

    private static List<RubricaComparada> rubricas(List<Apurado> apurados) {
        Map<UUID, RubricaDaLinha> todas = new LinkedHashMap<>();
        for (Apurado a : apurados) {
            a.porGrupo().values().stream().flatMap(List::stream)
                    .map(v -> a.entrada().rubricas().get(v.linha().getId())).filter(Objects::nonNull)
                    .forEach(r -> todas.putIfAbsent(r.id(), r));
        }
        List<RubricaComparada> lista = new ArrayList<>();
        for (RubricaDaLinha r : todas.values()) {
            List<ValorComparado> valores = valores(apurados, a -> {
                List<ValorLinha> linhas = a.porGrupo().values().stream().flatMap(List::stream)
                        .filter(v -> r.equals(a.entrada().rubricas().get(v.linha().getId()))).toList();
                return linhas.isEmpty() ? null
                        : new Parcial(linhas, linhas.size() == 1 ? linhas.getFirst().alvo() : null);
            });
            lista.add(new RubricaComparada(r.id(), r.nome(), r.grupo(), valores));
        }
        return List.copyOf(lista);
    }

    private static List<LinhaSemCorrespondencia> semCorrespondencia(List<Apurado> apurados) {
        List<LinhaSemCorrespondencia> lista = new ArrayList<>();
        for (Apurado a : apurados) {
            a.porGrupo().values().stream().flatMap(List::stream)
                    .filter(v -> !a.entrada().rubricas().containsKey(v.linha().getId()))
                    .forEach(v -> lista.add(new LinhaSemCorrespondencia(a.entrada().id(), v.linha().getId(),
                            v.linha().getEffectiveCode(), v.linha().getAccount(), v.linha().getDescription(), v.grupo(),
                            v.previstoMes(), v.previsto(), v.realizado(), v.alvo())));
        }
        return List.copyOf(lista);
    }

    /** Linhas de um exercício num grupo ou rubrica, e o alvo da evidência do conjunto. */
    private record Parcial(List<ValorLinha> linhas, String alvo) {
    }

    private static List<ValorComparado> valores(List<Apurado> apurados, Function<Apurado, Parcial> parcial) {
        List<Parcial> partes = apurados.stream().map(parcial).toList();
        List<ValorComparado> lista = new ArrayList<>();
        for (int i = 0; i < apurados.size(); i++) {
            Parcial p = partes.get(i);
            Apurado a = apurados.get(i);
            BigDecimal previstoMes = p == null ? null : soma(p.linhas().stream().map(ValorLinha::previstoMes).toList());
            BigDecimal previsto = p == null || !a.comRealizado() ? null
                    : somaOuNulo(p.linhas().stream().map(ValorLinha::previsto).toList());
            BigDecimal realizado = p == null || !a.comRealizado() ? null
                    : somaOuNulo(p.linhas().stream().map(ValorLinha::realizado).toList());
            Variacao vPrevisto = null;
            Variacao vRealizado = null;
            if (i + 1 < apurados.size()) {
                Parcial ant = partes.get(i + 1);
                Apurado aa = apurados.get(i + 1);
                BigDecimal antPrevistoMes = ant == null ? null
                        : soma(ant.linhas().stream().map(ValorLinha::previstoMes).toList());
                // Linha ou grupo que não existia no anterior: base zero ("nova no exercício")
                vPrevisto = previstoMes == null ? null : variacao(previstoMes, antPrevistoMes == null ? ZERO
                        : antPrevistoMes);
                BigDecimal antRealizado = ant == null || !aa.comRealizado() ? null
                        : somaOuNulo(ant.linhas().stream().map(ValorLinha::realizado).toList());
                vRealizado = variacao(realizado, antRealizado);
            }
            lista.add(new ValorComparado(a.entrada().id(), previstoMes, previsto, realizado, vPrevisto, vRealizado,
                    p == null ? null : p.alvo(), p == null ? List.of() : p.linhas().stream()
                            .map(v -> new LinhaUsada(v.linha().getId(), v.linha().getEffectiveCode(),
                                    v.linha().getDescription(), v.alvo())).toList()));
        }
        return List.copyOf(lista);
    }

    private static List<String> avisos(List<Entrada> entradas) {
        List<String> avisos = new ArrayList<>();
        for (Entrada e : entradas) {
            long sem = e.linhas().stream().filter(l -> l.getType() == BudgetLineType.LINHA)
                    .filter(l -> !e.rubricas().containsKey(l.getId())).count();
            if (sem > 0) {
                avisos.add(e.rotulo() + ": " + sem + (sem == 1 ? " linha" : " linhas")
                        + " sem rubrica confirmada (bloco \"sem correspondência\")");
            }
            if (!e.coluna() && e.periodo().isEmpty()) {
                avisos.add(e.rotulo() + ": sem fluxo carregado nos meses comparados (só previsto)");
            }
        }
        return List.copyOf(avisos);
    }

    private static String periodoDaEvidencia(Entrada e) {
        return e.mesesDoPeriodo().size() == 1 && e.periodo().size() == 1
                && !e.periodo().getFirst().periodo().equalsIgnoreCase("acumulado") ? e.mesesDoPeriodo().getFirst()
                : "acumulado";
    }

    private static BigDecimal valorDaLinha(Entrada e, BudgetLine l) {
        BigDecimal v = e.coluna() ? l.getPreviousBudgeted() : l.getBudgeted();
        return v == null ? ZERO : v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal soma(List<BigDecimal> valores) {
        return valores.stream().filter(Objects::nonNull).reduce(ZERO, BigDecimal::add);
    }

    /** Soma; nula se algum valor for nulo (número não apurado nunca vira zero, RF-03.1.10). */
    private static BigDecimal somaOuNulo(List<BigDecimal> valores) {
        return valores.stream().anyMatch(Objects::isNull) ? null : soma(valores);
    }

    private static <T> List<T> nulo(List<T> lista) {
        return lista == null ? List.of() : lista;
    }
}
