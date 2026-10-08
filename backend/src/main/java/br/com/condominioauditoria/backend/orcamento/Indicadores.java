package br.com.condominioauditoria.backend.orcamento;

import br.com.condominioauditoria.backend.orcamento.ComparacaoExercicios.GrupoComparado;
import br.com.condominioauditoria.backend.orcamento.ExercicioDtos.TipoExercicio;
import br.com.condominioauditoria.backend.orcamento.PrevistoRealizado.FluxoUsado;
import br.com.condominioauditoria.backend.orcamento.PrevistoRealizado.FundoResultado;
import br.com.condominioauditoria.backend.orcamento.PrevistoRealizado.GrupoResultado;
import br.com.condominioauditoria.backend.orcamento.PrevistoRealizado.LinhaResultado;
import br.com.condominioauditoria.backend.orcamento.PrevistoRealizado.MesExercicio;
import br.com.condominioauditoria.backend.orcamento.PrevistoRealizado.Regra20;
import br.com.condominioauditoria.backend.orcamento.PrevistoRealizado.Situacao;
import br.com.condominioauditoria.backend.orcamento.PrevistoRealizado.SituacaoFundo;
import br.com.condominioauditoria.backend.orcamento.PrevistoRealizado.SituacaoMes;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Séries dos 7 gráficos da tela "Indicadores" (RF-11.10 a RF-11.13; ADR 0005, Decisões 5 e 6). Função pura: recebe o
 * cálculo do acumulado e o de cada mês do exercício, os mesmos da tela de previsto × realizado, e só reorganiza os
 * números (premissa 4 do RF-11: os gráficos não calculam nada). Mês sem fluxo, ou com dois fluxos, vem com os números
 * nulos e a situação, nunca com zero. Cada ponto traz o mês e o alvo da evidência (RF-11.12).
 *
 * <p>Gráficos 1 a 5 são do fundo Condomínio; o 6, dos fundos ligados às linhas 1.9; o 7, da comparação de exercícios.
 * Filtro de fundo: o fundo Condomínio esconde o 6; outro fundo esconde 1 a 5 e deixa no 6 só ele.
 */
public final class Indicadores {

    /** Mais acima e mais abaixo do previsto no gráfico 5. */
    static final int MAIORES = 10;

    private Indicadores() {
    }

    /** Um mês do exercício e o seu cálculo (nulo sem fluxo carregado ou com dois fluxos). */
    public record MesCalculado(YearMonth mes, SituacaoMes situacao, PrevistoRealizado resultado) {
    }

    public record Entrada(PrevisaoOrcamentaria po, String rotulo, PrevistoRealizado acumulado,
            List<MesCalculado> meses, UUID fundoId, UUID fundoOrdinarioId, ComparacaoExercicios.Resultado comparacao,
            String semComparacao) {
    }

    /** Gráfico 1: realizado ÷ previsto do mês, com a referência de 100%. */
    public record PontoExecucao(String mes, SituacaoMes situacao, BigDecimal previsto, BigDecimal realizado,
            BigDecimal execucao, String alvo) {
    }

    /** Gráfico 2: excesso do mês em % do previsto, limite e cenário máximo (RF-03.1.11). */
    public record PontoRegra20(String mes, SituacaoMes situacao, BigDecimal excesso, BigDecimal percentual,
            BigDecimal limitePercentual, BigDecimal cenarioMaximo, BigDecimal percentualCenarioMaximo,
            Boolean acimaDoLimite, Boolean provisorio, String alvo) {
    }

    /** Gráfico 3: previsto e realizado somados até o mês (só os meses com fluxo entram na soma). */
    public record PontoAcumulado(String mes, SituacaoMes situacao, BigDecimal previstoAcumulado,
            BigDecimal realizadoAcumulado, String alvo) {
    }

    public record PontoValor(String mes, SituacaoMes situacao, BigDecimal valor, String alvo) {
    }

    /** Gráfico 4: realizado de um grupo (1.1 a 1.8), mês a mês. */
    public record SerieGrupo(String codigo, String descricao, List<PontoValor> pontos) {
    }

    /** Gráfico 5: diferença do acumulado de uma linha (realizado − previsto). */
    public record Diferenca(UUID linhaId, String codigo, String descricao, BigDecimal previsto, BigDecimal realizado,
            BigDecimal diferenca, String alvo) {
    }

    public record MaioresDiferencas(List<Diferenca> acima, List<Diferenca> abaixo) {
    }

    public record PontoFundo(String mes, SituacaoMes situacao, BigDecimal previsto, BigDecimal arrecadado,
            String alvo) {
    }

    /** Gráfico 6: arrecadação × previsto de um fundo ligado a uma linha 1.9. */
    public record SerieFundo(UUID fundoId, String fundo, String linhaCodigo, List<PontoFundo> pontos) {
    }

    /**
     * {@code poId}: a PO cuja evidência o clique abre no previsto × realizado; nulo na coluna impressa, que não tem
     * realizado.
     */
    public record ExercicioDaComparacao(String id, String rotulo, BigDecimal execucao, String periodo, UUID poId) {
    }

    /**
     * {@code alvos}: o alvo da evidência do grupo em cada exercício, na ordem de {@code exercicios} (o mesmo de
     * {@link ComparacaoExercicios.ValorComparado#alvo()}); nulo quando o grupo não existe no exercício ou o exercício
     * é a coluna impressa.
     */
    public record GrupoDaComparacao(String codigo, String descricao, List<BigDecimal> previstoMes,
            List<String> alvos) {
    }

    /**
     * Gráfico 7: previsto do mês por grupo em cada exercício e execução acumulada de cada um (RF-11.6). Valores na
     * ordem de {@code exercicios}.
     */
    public record Comparacao(List<ExercicioDaComparacao> exercicios, List<GrupoDaComparacao> grupos) {
    }

    /** {@code periodo}: meses do exercício (AAAA-MM). {@code dadosDe}: envio mais recente dos fluxos usados. */
    public record Resultado(UUID poId, String rotulo, String inicio, String fim, UUID fundoId, Instant dadosDe,
            BigDecimal limitePercentual, List<PontoExecucao> execucaoMensal, List<PontoRegra20> regra20,
            List<PontoAcumulado> acumulado, List<SerieGrupo> realizadoPorGrupo, MaioresDiferencas maioresDiferencas,
            List<SerieFundo> fundos, Comparacao comparacao, List<String> avisos) {
    }

    public static Resultado montar(Entrada e) {
        boolean outroFundo = e.fundoId() != null && !e.fundoId().equals(e.fundoOrdinarioId());
        boolean soCondominio = e.fundoId() != null && e.fundoId().equals(e.fundoOrdinarioId());
        List<String> avisos = new ArrayList<>();
        long semFluxo = e.meses().stream().filter(m -> m.situacao() != SituacaoMes.COM_FLUXO).count();
        if (semFluxo > 0) {
            avisos.add(semFluxo + (semFluxo == 1 ? " mês" : " meses") + " do exercício sem números (sem fluxo"
                    + " carregado ou com dois fluxos)");
        }
        if (e.comparacao() == null && e.semComparacao() != null) {
            avisos.add(e.semComparacao());
        }
        BigDecimal limite = e.meses().stream().map(MesCalculado::resultado).filter(Objects::nonNull)
                .map(PrevistoRealizado::regra20).filter(Objects::nonNull).map(Regra20::limitePercentual).findFirst()
                .orElse(null);
        return new Resultado(e.po().getId(), e.rotulo(), e.po().getExercicioInicio().toString(),
                e.po().getExercicioFim().toString(), e.fundoId(), dadosDe(e), limite,
                outroFundo ? null : execucao(e), outroFundo ? null : regra20(e), outroFundo ? null : acumulado(e),
                outroFundo ? null : porGrupo(e), outroFundo ? null : maioresDiferencas(e.acumulado()),
                soCondominio ? null : fundos(e), comparacao(e.comparacao()), List.copyOf(avisos));
    }

    private static List<PontoExecucao> execucao(Entrada e) {
        return e.meses().stream().map(m -> {
            PrevistoRealizado r = m.resultado();
            if (r == null) {
                return new PontoExecucao(m.mes().toString(), m.situacao(), null, null, null, null);
            }
            return new PontoExecucao(m.mes().toString(), m.situacao(), r.totais().previsto(),
                    r.totais().despesaRealizada(), r.totais().execucao(), CalculoPrevistoRealizado.ALVO_TOTAL);
        }).toList();
    }

    private static List<PontoRegra20> regra20(Entrada e) {
        return e.meses().stream().map(m -> {
            Regra20 r = m.resultado() == null ? null : m.resultado().regra20();
            if (r == null) {
                return new PontoRegra20(m.mes().toString(), m.situacao(), null, null, null, null, null, null, null,
                        null);
            }
            return new PontoRegra20(m.mes().toString(), m.situacao(), r.excesso(), r.percentual(),
                    r.limitePercentual(), r.cenarioMaximo(), r.percentualCenarioMaximo(), r.acimaDoLimite(),
                    r.provisorio(), CalculoPrevistoRealizado.ALVO_TOTAL);
        }).toList();
    }

    private static List<PontoAcumulado> acumulado(Entrada e) {
        List<PontoAcumulado> lista = new ArrayList<>();
        BigDecimal previsto = null;
        BigDecimal realizado = null;
        for (MesCalculado m : e.meses()) {
            PrevistoRealizado r = m.resultado();
            if (r == null) {
                lista.add(new PontoAcumulado(m.mes().toString(), m.situacao(), null, null, null));
                continue;
            }
            previsto = (previsto == null ? BigDecimal.ZERO.setScale(2) : previsto).add(r.totais().previsto());
            realizado = (realizado == null ? BigDecimal.ZERO.setScale(2) : realizado)
                    .add(r.totais().despesaRealizada());
            lista.add(new PontoAcumulado(m.mes().toString(), m.situacao(), previsto, realizado,
                    CalculoPrevistoRealizado.ALVO_TOTAL));
        }
        return List.copyOf(lista);
    }

    private static List<SerieGrupo> porGrupo(Entrada e) {
        // Grupos da PO na ordem do documento (1.1 a 1.8); os fundos ficam no gráfico 6
        Map<UUID, NomeGrupo> grupos = new LinkedHashMap<>();
        for (MesCalculado m : e.meses()) {
            if (m.resultado() == null) {
                continue;
            }
            for (GrupoResultado g : m.resultado().grupos()) {
                grupos.computeIfAbsent(g.linhaId(), k -> new NomeGrupo(g.codigo(), g.descricao()));
            }
        }
        if (grupos.isEmpty() && e.acumulado() != null && e.acumulado().grupos() != null) {
            e.acumulado().grupos().forEach(g -> grupos.put(g.linhaId(),
                    new NomeGrupo(g.codigo(), g.descricao())));
        }
        List<SerieGrupo> lista = new ArrayList<>();
        for (Map.Entry<UUID, NomeGrupo> x : grupos.entrySet()) {
            List<PontoValor> pontos = e.meses().stream().map(m -> {
                GrupoResultado g = m.resultado() == null ? null : m.resultado().grupos().stream()
                        .filter(gr -> gr.linhaId().equals(x.getKey())).findFirst().orElse(null);
                return g == null ? new PontoValor(m.mes().toString(), m.situacao(), null, null)
                        : new PontoValor(m.mes().toString(), m.situacao(), g.realizado(),
                                CalculoPrevistoRealizado.alvoGrupo(g.linhaId()));
            }).toList();
            lista.add(new SerieGrupo(x.getValue().codigo(), x.getValue().descricao(), pontos));
        }
        return List.copyOf(lista);
    }

    private record NomeGrupo(String codigo, String descricao) {
    }

    /** Gráfico 5, do acumulado: as 10 linhas mais acima (diferença &gt; 0) e as 10 mais abaixo (&lt; 0). */
    static MaioresDiferencas maioresDiferencas(PrevistoRealizado acumulado) {
        if (acumulado == null || acumulado.situacao() != Situacao.CALCULADO) {
            return new MaioresDiferencas(List.of(), List.of());
        }
        List<Diferenca> todas = acumulado.grupos().stream().flatMap(g -> g.linhas().stream())
                .map(Indicadores::diferenca).toList();
        List<Diferenca> acima = todas.stream().filter(d -> d.diferenca().signum() > 0)
                .sorted(Comparator.comparing(Diferenca::diferenca).reversed().thenComparing(Diferenca::codigo))
                .limit(MAIORES).toList();
        List<Diferenca> abaixo = todas.stream().filter(d -> d.diferenca().signum() < 0)
                .sorted(Comparator.comparing(Diferenca::diferenca).thenComparing(Diferenca::codigo))
                .limit(MAIORES).toList();
        return new MaioresDiferencas(acima, abaixo);
    }

    private static Diferenca diferenca(LinhaResultado l) {
        return new Diferenca(l.linhaId(), l.codigo(), l.descricao(), l.previsto(), l.realizado(), l.diferenca(),
                CalculoPrevistoRealizado.alvoLinha(l.linhaId()));
    }

    private static List<SerieFundo> fundos(Entrada e) {
        Map<UUID, FundoResultado> doExercicio = new LinkedHashMap<>();
        List<PrevistoRealizado> resultados = new ArrayList<>();
        if (e.acumulado() != null && e.acumulado().fundos() != null) {
            resultados.add(e.acumulado());
        }
        e.meses().stream().map(MesCalculado::resultado).filter(Objects::nonNull).forEach(resultados::add);
        for (PrevistoRealizado r : resultados) {
            for (FundoResultado f : r.fundos()) {
                if (f.fundoId() != null && f.linhaId() != null
                        && (e.fundoId() == null || e.fundoId().equals(f.fundoId()))) {
                    doExercicio.putIfAbsent(f.fundoId(), f);
                }
            }
        }
        List<SerieFundo> lista = new ArrayList<>();
        for (FundoResultado f : doExercicio.values()) {
            List<PontoFundo> pontos = e.meses().stream().map(m -> {
                FundoResultado doMes = m.resultado() == null ? null : m.resultado().fundos().stream()
                        .filter(x -> f.fundoId().equals(x.fundoId()) && x.situacao() == SituacaoFundo.COMPARADO)
                        .findFirst().orElse(null);
                return doMes == null ? new PontoFundo(m.mes().toString(), m.situacao(), null, null, null)
                        : new PontoFundo(m.mes().toString(), m.situacao(), doMes.previsto(), doMes.arrecadado(),
                                CalculoPrevistoRealizado.alvoFundo(f.fundoId()));
            }).toList();
            lista.add(new SerieFundo(f.fundoId(), f.fundo(), f.linhaCodigo(), pontos));
        }
        return List.copyOf(lista);
    }

    private static Comparacao comparacao(ComparacaoExercicios.Resultado c) {
        if (c == null) {
            return null;
        }
        List<ExercicioDaComparacao> exercicios = new ArrayList<>();
        for (int i = 0; i < c.exercicios().size(); i++) {
            var ex = c.exercicios().get(i);
            exercicios.add(new ExercicioDaComparacao(ex.id(), ex.rotulo(), c.resumo().get(i).execucao(),
                    ex.periodo(), ex.tipo() == TipoExercicio.COLUNA_IMPRESSA ? null : ex.poId()));
        }
        List<GrupoDaComparacao> grupos = new ArrayList<>();
        for (GrupoComparado g : c.grupos()) {
            grupos.add(new GrupoDaComparacao(g.codigo(), g.descricao(),
                    g.valores().stream().map(ComparacaoExercicios.ValorComparado::previstoMes).toList(),
                    g.valores().stream().map(ComparacaoExercicios.ValorComparado::alvo).toList()));
        }
        return new Comparacao(List.copyOf(exercicios), List.copyOf(grupos));
    }

    private static Instant dadosDe(Entrada e) {
        return e.meses().stream().map(MesCalculado::resultado).filter(Objects::nonNull)
                .flatMap(r -> r.meses().stream()).map(MesExercicio::fluxos).flatMap(List::stream)
                .map(FluxoUsado::enviadoEm).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
    }
}
