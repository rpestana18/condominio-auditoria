package br.com.condominioauditoria.backend.orcamento;

import br.com.condominioauditoria.backend.auditoria.RegraExcessoMes;
import br.com.condominioauditoria.backend.contabil.ImpressaoLancamento;
import br.com.condominioauditoria.backend.contabil.Lancamento;
import br.com.condominioauditoria.backend.orcamento.PrevistoRealizado.Aviso;
import br.com.condominioauditoria.backend.orcamento.PrevistoRealizado.Bloco;
import br.com.condominioauditoria.backend.orcamento.PrevistoRealizado.ConferenciaFluxo;
import br.com.condominioauditoria.backend.orcamento.PrevistoRealizado.ContaBloco;
import br.com.condominioauditoria.backend.orcamento.PrevistoRealizado.Evidencia;
import br.com.condominioauditoria.backend.orcamento.PrevistoRealizado.FluxoUsado;
import br.com.condominioauditoria.backend.orcamento.PrevistoRealizado.FundoResultado;
import br.com.condominioauditoria.backend.orcamento.PrevistoRealizado.GrupoResultado;
import br.com.condominioauditoria.backend.orcamento.PrevistoRealizado.LinhaExcesso;
import br.com.condominioauditoria.backend.orcamento.PrevistoRealizado.LinhaResultado;
import br.com.condominioauditoria.backend.orcamento.PrevistoRealizado.MesExercicio;
import br.com.condominioauditoria.backend.orcamento.PrevistoRealizado.PoResumo;
import br.com.condominioauditoria.backend.orcamento.PrevistoRealizado.Regra20;
import br.com.condominioauditoria.backend.orcamento.PrevistoRealizado.ResumoDeparaPeriodo;
import br.com.condominioauditoria.backend.orcamento.PrevistoRealizado.Situacao;
import br.com.condominioauditoria.backend.orcamento.PrevistoRealizado.SituacaoFundo;
import br.com.condominioauditoria.backend.orcamento.PrevistoRealizado.SituacaoMes;
import br.com.condominioauditoria.backend.orcamento.PrevistoRealizado.Totais;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Previsto × realizado (RF-03.1.6 a RF-03.1.11; ADR 0004, Decisão 5). <b>Função pura</b>: não acessa banco, relógio
 * nem rede. Mesmo insumo e mesma {@link #VERSAO} = mesmo resultado. A tela, a exportação e o golden usam esta função.
 *
 * <p>Regras (respostas Q18 a Q26 e Q30):
 * <ul>
 * <li>mês = mês da data do lançamento; valor = débito do lançamento como está no fluxo;</li>
 * <li>realizado = débitos do fundo Condomínio (fundo ordinário confirmado), pelo de-para <b>confirmado</b>:
 * linha da PO; AJUSTE vai para "ajustes" (não é despesa); A_REALOCAR vai para "a realocar" até haver realocação;
 * TRANSFERENCIA e lançamento marcado como transferência entre fundos ficam fora; conta sem de-para confirmado vai
 * para "sem linha da PO" e nunca é somada a outra linha;</li>
 * <li>previsto da linha = orçado (mensal, igual em todos os meses); previsto do mês = soma das linhas dos grupos de
 * despesa (Q30), nunca o total impresso;</li>
 * <li>fundos ligados às linhas 1.9: arrecadação = créditos de recebimento de cota (Q25), nunca débitos;</li>
 * <li>mês sem fluxo carregado não vira zero; dois fluxos no mesmo mês não são somados;</li>
 * <li>regra dos 20% pela {@link RegraExcessoMes}; percentuais com 10 casas, exibidos com 1 (meio para cima).</li>
 * </ul>
 */
public final class CalculoPrevistoRealizado {

    /**
     * Versão das regras deste cálculo; muda quando qualquer regra acima muda. Vai em todo resultado. Versão 2: a
     * realocação casa com o lançamento pela chave estável (impressão), não pelo id.
     */
    public static final String VERSAO = "2";

    public static final String ALVO_AJUSTES = "AJUSTES";
    public static final String ALVO_A_REALOCAR = "A_REALOCAR";
    public static final String ALVO_SEM_LINHA_PO = "SEM_LINHA_PO";
    public static final String ALVO_TRANSFERENCIAS = "TRANSFERENCIAS";
    /** Evidência da despesa realizada inteira: linhas da PO + a realocar + sem linha da PO (RF-03.1.12). */
    public static final String ALVO_TOTAL = "total";

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal CEM = new BigDecimal("100");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final String[] MESES = {"jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov",
            "dez"};

    public sealed interface Periodo permits Mes, Acumulado {
    }

    public record Mes(YearMonth mes) implements Periodo {
    }

    /** Do início do exercício até o último mês com fluxo carregado. */
    public record Acumulado() implements Periodo {
    }

    /** Arquivo concluído da categoria de balancetes e fluxos de caixa, com o período lido. */
    public record Fluxo(UUID arquivoId, String nome, String sha256, LocalDate periodoInicio, LocalDate periodoFim,
            Instant enviadoEm, String enviadoPor) {

        boolean cobre(YearMonth mes) {
            return periodoInicio != null && periodoFim != null && !periodoInicio.isAfter(mes.atEndOfMonth())
                    && !periodoFim.isBefore(mes.atDay(1));
        }

        FluxoUsado usado() {
            return new FluxoUsado(arquivoId, nome, sha256, periodoInicio, periodoFim, enviadoEm, enviadoPor);
        }
    }

    /**
     * Lançamento a realocar levado a uma linha da PO (RF-03.1.7). Casa com o lançamento pela {@code chave} da
     * {@link ImpressaoLancamento}, que sobrevive ao reprocesso do fluxo; data, conta, valor, arquivo e página servem
     * para o aviso quando não há casamento.
     */
    public record Realocacao(UUID id, String chave, UUID arquivoId, LocalDate data, String conta, BigDecimal valor,
            int pagina, UUID linhaPoId, String usuario, Instant em) {

        public Realocacao {
            Objects.requireNonNull(chave, "chave");
            Objects.requireNonNull(data, "data");
            Objects.requireNonNull(linhaPoId, "linhaPoId");
        }
    }

    /**
     * @param fundoPorLinha linha 1.9.x → fundo do fluxo ligado pelo Admin
     * @param limiteExcessoPercentual parâmetro da Conv. 16.2 vigente no período (nulo: regra não avaliada)
     * @param avisosDaPo avisos da própria PO (arredondamento, confirmada com divergência), repetidos no resultado
     */
    public record Entrada(PrevisaoOrcamentaria po, String poArquivoNome, List<LinhaPo> linhas, List<DeparaConta> deparas,
            Map<UUID, UUID> fundoPorLinha, Map<UUID, String> nomesFundos, UUID fundoOrdinarioId, List<Fluxo> fluxos,
            List<Lancamento> lancamentos, List<Realocacao> realocacoes, BigDecimal limiteExcessoPercentual,
            List<Aviso> avisosDaPo, Periodo periodo) {

        public Entrada {
            Objects.requireNonNull(periodo, "periodo");
            linhas = linhas == null ? List.of() : List.copyOf(linhas);
            deparas = deparas == null ? List.of() : List.copyOf(deparas);
            fundoPorLinha = fundoPorLinha == null ? Map.of() : Map.copyOf(fundoPorLinha);
            nomesFundos = nomesFundos == null ? Map.of() : Map.copyOf(nomesFundos);
            fluxos = fluxos == null ? List.of() : List.copyOf(fluxos);
            lancamentos = lancamentos == null ? List.of() : List.copyOf(lancamentos);
            realocacoes = realocacoes == null ? List.of() : List.copyOf(realocacoes);
            avisosDaPo = avisosDaPo == null ? List.of() : List.copyOf(avisosDaPo);
        }
    }

    /** Resultado e os lançamentos de cada número, pelo alvo ("linha:&lt;id&gt;", "fundo:&lt;id&gt;", AJUSTES...). */
    public record Calculo(PrevistoRealizado resultado, Map<String, List<Evidencia>> evidencias) {
    }

    private CalculoPrevistoRealizado() {
    }

    public static String alvoLinha(UUID linhaId) {
        return "linha:" + linhaId;
    }

    public static String alvoFundo(UUID fundoId) {
        return "fundo:" + fundoId;
    }

    /** Evidência de um grupo da PO: os lançamentos das linhas dele (pelo id da linha de grupo). */
    public static String alvoGrupo(UUID linhaDoGrupoId) {
        return "grupo:" + linhaDoGrupoId;
    }

    /**
     * Lançamentos que compõem um número (RF-03.1.12): "linha:&lt;id&gt;", "grupo:&lt;id&gt;" (as linhas do grupo, na
     * ordem da PO), "total" (despesa realizada: as linhas de todos os grupos, depois a realocar e sem linha da PO),
     * "fundo:&lt;id&gt;" e os blocos (AJUSTES, A_REALOCAR, SEM_LINHA_PO, TRANSFERENCIAS). Alvo sem lançamento: lista
     * vazia. Só lê o que o cálculo já apurou.
     */
    public static List<Evidencia> evidencia(Calculo c, String alvo) {
        String a = alvo == null ? "" : alvo.trim();
        PrevistoRealizado r = c.resultado();
        if (a.startsWith("grupo:")) {
            return r.grupos().stream().filter(g -> alvoGrupo(g.linhaId()).equals(a)).findFirst()
                    .map(g -> doGrupo(c, g)).orElse(List.of());
        }
        if (a.equals(ALVO_TOTAL)) {
            List<Evidencia> todas = new ArrayList<>();
            r.grupos().forEach(g -> todas.addAll(doGrupo(c, g)));
            todas.addAll(c.evidencias().getOrDefault(ALVO_A_REALOCAR, List.of()));
            todas.addAll(c.evidencias().getOrDefault(ALVO_SEM_LINHA_PO, List.of()));
            return List.copyOf(todas);
        }
        return c.evidencias().getOrDefault(a, List.of());
    }

    private static List<Evidencia> doGrupo(Calculo c, GrupoResultado g) {
        List<Evidencia> lista = new ArrayList<>();
        g.linhas().forEach(l -> lista.addAll(c.evidencias().getOrDefault(alvoLinha(l.linhaId()), List.of())));
        return List.copyOf(lista);
    }

    public static Calculo calcular(Entrada e) {
        String periodo = e.periodo() instanceof Mes m ? m.mes().toString() : "acumulado";
        PrevisaoOrcamentaria po = e.po();
        if (po == null) {
            return vazio(periodo, Situacao.SEM_PO, e.periodo() instanceof Mes m
                    ? "Sem PO aprovada para " + mmaaaa(m.mes()) : "Sem PO aprovada", null, List.of(), e);
        }
        PoResumo resumoPo = new PoResumo(po.getId(), po.getVersao(), po.getEstado(), po.getArquivoId(),
                e.poArquivoNome(), po.getSha256(), ConsultaPrevisao.mes(po.getExercicioInicio()),
                ConsultaPrevisao.mes(po.getExercicioFim()));
        VigenciaPo vigencia = VigenciaPo.de(po).orElse(null);
        if (vigencia == null) {
            return vazio(periodo, Situacao.PO_NAO_CONFIRMADA, "PO não confirmada: o Admin confirma a PO antes do"
                    + " previsto × realizado", resumoPo, List.of(), e);
        }
        // Prorrogação (RF-11.3): o mês depois do exercício usa esta PO, com a marca "PO prorrogada"
        VigenciaPo prorrogacao = VigenciaPo.prorrogacao(po).orElse(null);
        boolean mesProrrogado = e.periodo() instanceof Mes m && !vigencia.cobre(m.mes()) && prorrogacao != null
                && prorrogacao.cobre(m.mes());
        if (e.periodo() instanceof Mes m && !vigencia.cobre(m.mes()) && !mesProrrogado) {
            return vazio(periodo, Situacao.SEM_PO, "Sem PO aprovada para " + mmaaaa(m.mes()), resumoPo, List.of(), e);
        }
        if (e.fundoOrdinarioId() == null) {
            return vazio(periodo, Situacao.SEM_FUNDO_ORDINARIO, "Confirme o fundo ordinário (fundo Condomínio) do"
                    + " condomínio para calcular o realizado", resumoPo, List.of(), e);
        }
        Base base = new Base(comAviso(e, avisoProrrogacao(po, vigencia, prorrogacao, e.periodo(), mesProrrogado)));

        if (e.periodo() instanceof Mes m) {
            List<Fluxo> doMes = base.fluxosDoMes(m.mes());
            if (doMes.isEmpty()) {
                return vazio(periodo, Situacao.SEM_FLUXO, "Sem fluxo carregado para " + mmaaaa(m.mes()), resumoPo,
                        List.of(mesSemNumeros(m.mes(), SituacaoMes.SEM_FLUXO, doMes).comProrrogado(mesProrrogado)),
                        base.e);
            }
            if (doMes.size() > 1) {
                return vazio(periodo, Situacao.DOIS_FLUXOS, "Dois fluxos para " + mmaaaa(m.mes())
                                + ": substitua, reclassifique ou exclua um", resumoPo,
                        List.of(mesSemNumeros(m.mes(), SituacaoMes.DOIS_FLUXOS, doMes).comProrrogado(mesProrrogado)),
                        base.e);
            }
            Apuracao ap = base.apurar(Map.of(m.mes(), doMes.getFirst()));
            MesExercicio mes = base.resumoDoMes(m.mes(), doMes.getFirst(), ap).comProrrogado(mesProrrogado);
            return base.montar(periodo, resumoPo, ap, 1, List.of(mes), List.of(m.mes().toString()), List.of(),
                    List.of(), true);
        }

        // Acumulado do exercício (RF-03.1.10): só os meses com fluxo, nos dois lados
        List<YearMonth> exercicio = meses(vigencia.inicio(), vigencia.fim());
        Map<YearMonth, List<Fluxo>> porMes = new LinkedHashMap<>();
        exercicio.forEach(mes -> porMes.put(mes, base.fluxosDoMes(mes)));
        YearMonth ultimo = exercicio.stream().filter(mes -> !porMes.get(mes).isEmpty()).reduce((a, b) -> b).orElse(null);
        Map<YearMonth, Fluxo> escolhidos = new TreeMap<>();
        List<MesExercicio> meses = new ArrayList<>();
        List<String> somados = new ArrayList<>();
        List<YearMonth> faltando = new ArrayList<>();
        List<String> duplos = new ArrayList<>();
        for (YearMonth mes : exercicio) {
            List<Fluxo> f = porMes.get(mes);
            if (f.size() == 1) {
                escolhidos.put(mes, f.getFirst());
                somados.add(mes.toString());
                meses.add(base.resumoDoMes(mes, f.getFirst(), base.apurar(Map.of(mes, f.getFirst()))));
            } else if (f.isEmpty()) {
                meses.add(mesSemNumeros(mes, SituacaoMes.SEM_FLUXO, f));
                if (ultimo != null && mes.isBefore(ultimo)) {
                    faltando.add(mes);
                }
            } else {
                meses.add(mesSemNumeros(mes, SituacaoMes.DOIS_FLUXOS, f));
                duplos.add(mes.toString());
            }
        }
        // Meses prorrogados: depois dos 12, com os números de cada mês, fora da soma do acumulado (RF-11.3)
        if (prorrogacao != null) {
            for (YearMonth mes : meses(prorrogacao.inicio(), prorrogacao.fim())) {
                List<Fluxo> f = base.fluxosDoMes(mes);
                MesExercicio m = f.size() == 1 ? base.resumoDoMes(mes, f.getFirst(), base.apurar(Map.of(mes, f.getFirst())))
                        : mesSemNumeros(mes, f.isEmpty() ? SituacaoMes.SEM_FLUXO : SituacaoMes.DOIS_FLUXOS, f);
                meses.add(m.comProrrogado(true));
            }
        }
        if (escolhidos.isEmpty()) {
            Situacao s = duplos.isEmpty() ? Situacao.SEM_FLUXO : Situacao.DOIS_FLUXOS;
            PrevistoRealizado r = vazio(periodo, s, duplos.isEmpty() ? "Nenhum mês do exercício com fluxo carregado"
                    : "Os meses com fluxo têm dois fluxos cada: substitua, reclassifique ou exclua um", resumoPo, meses,
                    base.e)
                    .resultado();
            return new Calculo(new PrevistoRealizado(r.versaoCalculo(), r.periodo(), r.situacao(), r.mensagem(), r.po(),
                    r.meses(), List.of(), faltando.stream().map(YearMonth::toString).toList(), List.copyOf(duplos), null,
                    false, null, List.of(), null, null, null, null, null, List.of(), r.avisos()), Map.of());
        }
        Apuracao ap = base.apurar(escolhidos);
        return base.montar(periodo, resumoPo, ap, escolhidos.size(), meses, somados,
                faltando.stream().map(YearMonth::toString).toList(), duplos, false);
    }

    /** O que vale para o período inteiro: linhas, destinos, de-para confirmado e fundos ligados. */
    private static final class Base {

        final Entrada e;
        final EstruturaPo estrutura;
        final Map<UUID, LinhaPo> destinosValidos = new LinkedHashMap<>();
        final Map<String, Destino> confirmados;
        final Map<String, DeparaConta> deparaPorConta = new HashMap<>();
        final Map<String, Realocacao> realocacoes = new HashMap<>();
        final Map<UUID, LinhaPo> linhaPorFundo = new HashMap<>();
        final List<LinhaPo> linhasDeFundo;
        final Map<UUID, LinhaPo> linhasPorId;

        Base(Entrada e) {
            this.e = e;
            this.estrutura = EstruturaPo.de(e.linhas());
            ServicoDepara.destinosDeDebito(estrutura).forEach(l -> destinosValidos.put(l.getId(), l));
            this.confirmados = DeparaEfetivo.confirmados(e.deparas());
            e.deparas().forEach(d -> deparaPorConta.put(d.getContaCodigo(), d));
            e.realocacoes().forEach(r -> realocacoes.put(r.chave(), r));
            this.linhasPorId = e.linhas().stream().collect(Collectors.toMap(LinhaPo::getId, Function.identity()));
            this.linhasDeFundo = estrutura.fundos().map(EstruturaPo.Grupo::linhas).orElse(List.of());
            linhasDeFundo.forEach(l -> {
                UUID fundo = e.fundoPorLinha().get(l.getId());
                if (fundo != null) {
                    linhaPorFundo.put(fundo, l);
                }
            });
        }

        List<Fluxo> fluxosDoMes(YearMonth mes) {
            return e.fluxos().stream().filter(f -> f.cobre(mes))
                    .sorted(Comparator.comparing(Fluxo::enviadoEm, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(Fluxo::arquivoId))
                    .toList();
        }

        BigDecimal previstoMes() {
            return estrutura.previstoMesPelasLinhas().setScale(2, RoundingMode.UNNECESSARY);
        }

        /** Soma os lançamentos do fluxo escolhido para cada mês (só os do próprio mês, pela data). */
        Apuracao apurar(Map<YearMonth, Fluxo> escolhidos) {
            Apuracao ap = new Apuracao();
            List<Lancamento> ordenados = e.lancamentos().stream()
                    .sorted(Comparator.comparing(Lancamento::getData).thenComparing(Lancamento::getPagina)
                            .thenComparing(Lancamento::getOrdem).thenComparing(Lancamento::getId))
                    .toList();
            for (Lancamento l : ordenados) {
                Fluxo f = escolhidos.get(YearMonth.from(l.getData()));
                if (f == null || !f.arquivoId().equals(l.getArquivoId())) {
                    continue;
                }
                if (l.getFundoId().equals(e.fundoOrdinarioId())) {
                    condominio(ap, l, f);
                } else {
                    outroFundo(ap, l, f);
                }
            }
            return ap;
        }

        private void condominio(Apuracao ap, Lancamento l, Fluxo f) {
            BigDecimal valor = l.getDebito();
            if (valor.signum() == 0) {
                return;
            }
            ap.debitos = ap.debitos.add(valor);
            ap.qtdDebitos++;
            String conta = l.getContaCodigo();
            if (l.isTransferenciaEntreFundos()) {
                ap.transferencias.somar(conta, l.getContaNome(), "transferência entre fundos", valor);
                ap.evidencia(ALVO_TRANSFERENCIAS, l, f, nome(l.getFundoId()), null);
                return;
            }
            if (conta != null) {
                ap.contas.add(conta);
            }
            Destino d = conta == null ? null : confirmados.get(conta);
            if (d == null || (d.tipo() == TipoDestino.LINHA_PO && !destinosValidos.containsKey(d.linhaPoId()))) {
                DeparaConta pendente = conta == null ? null : deparaPorConta.get(conta);
                String detalhe = conta == null ? "lançamento sem conta"
                        : pendente == null ? "sem de-para"
                        : pendente.getEstado() == EstadoDepara.CONFIRMADO ? "destino inválido"
                        : "de-para " + pendente.getEstado().name().toLowerCase();
                ap.semLinha.somar(conta, l.getContaNome(), detalhe, valor);
                if (conta != null) {
                    ap.contasSemDepara.add(conta);
                }
                ap.evidencia(ALVO_SEM_LINHA_PO, l, f, nome(l.getFundoId()), null);
                return;
            }
            ap.contasConfirmadas.add(conta);
            switch (d.tipo()) {
                case LINHA_PO -> ap.linha(d.linhaPoId(), valor, l, f, nome(l.getFundoId()), null, null);
                case AJUSTE -> {
                    ap.ajustes.somar(conta, l.getContaNome(), d.texto(), valor);
                    ap.evidencia(ALVO_AJUSTES, l, f, nome(l.getFundoId()), null);
                }
                case TRANSFERENCIA -> {
                    ap.transferencias.somar(conta, l.getContaNome(), d.texto(), valor);
                    ap.evidencia(ALVO_TRANSFERENCIAS, l, f, nome(l.getFundoId()), null);
                }
                case A_REALOCAR -> {
                    Realocacao r = realocacoes.get(ImpressaoLancamento.chave(l));
                    LinhaPo destino = r == null ? null : destinosValidos.get(r.linhaPoId());
                    if (destino != null) {
                        ap.realocacoesUsadas.add(r.chave());
                        ap.linha(destino.getId(), valor, l, f, nome(l.getFundoId()), "realocado para "
                                + destino.getCodigoEfetivo() + " " + destino.getDescricao() + " por " + r.usuario()
                                + " em " + DATA.format(r.em().atZone(FUSO)), r.id());
                    } else {
                        ap.aRealocar.somar(conta, l.getContaNome(), d.texto(), valor);
                        ap.evidencia(ALVO_A_REALOCAR, l, f, nome(l.getFundoId()), null);
                    }
                }
            }
        }

        private void outroFundo(Apuracao ap, Lancamento l, Fluxo f) {
            MovimentoFundo m = ap.fundos.computeIfAbsent(l.getFundoId(), k -> new MovimentoFundo());
            m.creditos = m.creditos.add(l.getCredito());
            m.debitos = m.debitos.add(l.getDebito());
            if (l.getCredito().signum() != 0 && !l.isTransferenciaEntreFundos()) {
                if (l.getRecebimentoCota() == null) {
                    m.reprocessar = true;
                } else if (l.getRecebimentoCota()) {
                    m.arrecadado = m.arrecadado.add(l.getCredito());
                    ap.evidencia(alvoFundo(l.getFundoId()), l, f, nome(l.getFundoId()), null);
                }
            }
        }

        String nome(UUID fundoId) {
            return e.nomesFundos().get(fundoId);
        }

        MesExercicio resumoDoMes(YearMonth mes, Fluxo f, Apuracao ap) {
            BigDecimal previsto = previstoMes();
            BigDecimal despesa = ap.despesa();
            BigDecimal excesso = excesso(ap, 1);
            var regra = e.limiteExcessoPercentual() == null ? null
                    : RegraExcessoMes.avaliar(excesso, previsto, e.limiteExcessoPercentual()).orElse(null);
            return new MesExercicio(mes.toString(), SituacaoMes.COM_FLUXO, List.of(f.usado()), previsto, despesa, excesso,
                    regra == null ? null : umaCasa(regra.percentual()), regra == null ? null : regra.acimaDoLimite(),
                    false);
        }

        BigDecimal excesso(Apuracao ap, int meses) {
            BigDecimal soma = ZERO;
            for (LinhaPo l : destinosValidos.values()) {
                BigDecimal dif = ap.realizado(l.getId()).subtract(previsto(l, meses));
                if (dif.signum() > 0) {
                    soma = soma.add(dif);
                }
            }
            return soma;
        }

        static BigDecimal previsto(LinhaPo l, int meses) {
            return l.getOrcado().multiply(BigDecimal.valueOf(meses)).setScale(2, RoundingMode.UNNECESSARY);
        }

        Calculo montar(String periodo, PoResumo po, Apuracao ap, int n, List<MesExercicio> meses, List<String> somados,
                List<String> faltando, List<String> duplos, boolean comRegra20) {
            List<GrupoResultado> grupos = new ArrayList<>();
            Map<UUID, List<String>> contasPorLinha = new HashMap<>();
            confirmados.forEach((conta, d) -> {
                if (d.tipo() == TipoDestino.LINHA_PO) {
                    contasPorLinha.computeIfAbsent(d.linhaPoId(), k -> new ArrayList<>()).add(conta);
                }
            });
            BigDecimal previstoTotal = ZERO;
            BigDecimal emLinhas = ZERO;
            for (EstruturaPo.Grupo g : estrutura.gruposSemFundos()) {
                List<LinhaResultado> linhas = new ArrayList<>();
                BigDecimal gp = ZERO;
                BigDecimal gr = ZERO;
                for (LinhaPo l : g.linhas()) {
                    BigDecimal p = previsto(l, n);
                    BigDecimal r = ap.realizado(l.getId());
                    gp = gp.add(p);
                    gr = gr.add(r);
                    linhas.add(new LinhaResultado(l.getId(), l.getCodigoEfetivo(), l.getDescricao(), l.getConta(),
                            l.getMarca(), l.getObservacoes(), l.getPagina(), p, r, r.subtract(p), percentual(r, p),
                            contasPorLinha.getOrDefault(l.getId(), List.of()).stream().sorted().toList(),
                            ap.qtdPorLinha.getOrDefault(l.getId(), 0)));
                }
                previstoTotal = previstoTotal.add(gp);
                emLinhas = emLinhas.add(gr);
                grupos.add(new GrupoResultado(g.linha().getId(), g.linha().getCodigoEfetivo(), g.linha().getDescricao(),
                        gp, gr, gr.subtract(gp), percentual(gr, gp), List.copyOf(linhas)));
            }
            BigDecimal despesa = ap.despesa();
            BigDecimal previstoMes = previstoMes();
            int mesesExercicio = meses(e.po().getExercicioInicio(), e.po().getExercicioFim()).size();
            Totais totais = new Totais(previstoMes, previstoTotal, despesa, emLinhas, despesa.subtract(previstoTotal),
                    percentual(despesa, previstoTotal),
                    previstoMes.multiply(BigDecimal.valueOf(mesesExercicio)).setScale(2, RoundingMode.UNNECESSARY));
            Bloco ajustes = ap.ajustes.bloco();
            Bloco aRealocar = ap.aRealocar.bloco();
            Bloco semLinha = ap.semLinha.bloco();
            BigDecimal transferencias = ap.transferencias.total;
            ConferenciaFluxo conferencia = new ConferenciaFluxo(ap.debitos, ap.qtdDebitos, despesa, ajustes.total(),
                    transferencias, ap.debitos.compareTo(despesa.add(ajustes.total()).add(transferencias)) == 0);
            boolean provisorio = aRealocar.lancamentos() > 0 || semLinha.lancamentos() > 0;

            List<Aviso> avisos = new ArrayList<>(e.avisosDaPo());
            Regra20 regra20 = null;
            if (comRegra20) {
                regra20 = regra20(ap, n, previstoTotal, aRealocar.total(), semLinha.total(), provisorio, avisos);
            }
            int semDepara = ap.contasSemDepara.size();
            if (semDepara > 0) {
                avisos.add(new Aviso("SEM_DEPARA_CONFIRMADO", semDepara + (semDepara == 1 ? " conta" : " contas")
                        + " sem de-para confirmado (sem linha da PO): " + DinheiroBr.formatar(semLinha.total())
                        + " fora das linhas da PO"));
            }
            if (semLinha.contas().stream().anyMatch(c -> c.conta() == null)) {
                avisos.add(new Aviso("LANCAMENTO_SEM_CONTA", "Lançamentos sem conta do fluxo ficam em \"sem linha da"
                        + " PO\""));
            }
            if (aRealocar.lancamentos() > 0) {
                avisos.add(new Aviso("A_REALOCAR", DinheiroBr.formatar(aRealocar.total()) + " a realocar ("
                        + aRealocar.lancamentos() + " lançamentos): fora das linhas da PO até a realocação"));
            }
            realocacoesSemEfeito(ap, somados).forEach(avisos::add);
            if (!faltando.isEmpty()) {
                avisos.add(new Aviso("MESES_SEM_FLUXO", listaDeMeses(faltando) + " sem fluxo carregado"));
            }
            duplos.forEach(m -> avisos.add(new Aviso("DOIS_FLUXOS", mmaaaa(YearMonth.parse(m)) + " com dois fluxos:"
                    + " substitua, reclassifique ou exclua um")));
            List<FundoResultado> fundos = fundos(ap, n, avisos);
            if (!conferencia.confere()) {
                avisos.add(new Aviso("CONFERENCIA_FLUXO", "Total de débitos do fundo diferente de despesa + ajustes +"
                        + " transferências"));
            }

            Set<String> contas = new TreeSet<>(ap.contas);
            ResumoDeparaPeriodo depara = new ResumoDeparaPeriodo(contas.size(),
                    (int) contas.stream().filter(ap.contasConfirmadas::contains).count(), semDepara);
            PrevistoRealizado r = new PrevistoRealizado(VERSAO, periodo, Situacao.CALCULADO, null, po, List.copyOf(meses),
                    List.copyOf(somados), List.copyOf(faltando), List.copyOf(duplos), depara, provisorio, totais,
                    List.copyOf(grupos), ajustes, aRealocar, semLinha, conferencia, regra20, fundos, List.copyOf(avisos));
            Map<String, List<Evidencia>> evidencias = new TreeMap<>();
            ap.evidencias.forEach((k, v) -> evidencias.put(k, List.copyOf(v)));
            return new Calculo(r, evidencias);
        }

        /**
         * Realocações de meses somados que não entraram em nenhuma linha: sem lançamento correspondente (o fluxo
         * mudou) ou com lançamento que não está mais em "a realocar". Nada é somado em silêncio.
         */
        private List<Aviso> realocacoesSemEfeito(Apuracao ap, List<String> somados) {
            Set<String> chavesDoPeriodo = e.lancamentos().stream().map(ImpressaoLancamento::chave)
                    .collect(Collectors.toSet());
            List<Aviso> avisos = new ArrayList<>();
            e.realocacoes().stream().filter(r -> somados.contains(YearMonth.from(r.data()).toString()))
                    .filter(r -> !ap.realocacoesUsadas.contains(r.chave()))
                    .sorted(Comparator.comparing(Realocacao::data).thenComparing(Realocacao::chave))
                    .forEach(r -> {
                        String quem = "lançamento de " + DATA.format(r.data()) + (r.conta() == null ? ""
                                : ", conta " + r.conta()) + ", R$ " + DinheiroBr.formatar(r.valor()) + ", página "
                                + r.pagina();
                        if (!chavesDoPeriodo.contains(r.chave())) {
                            avisos.add(new Aviso("REALOCACAO_SEM_LANCAMENTO", "Realocação sem lançamento"
                                    + " correspondente (" + quem + "): o fluxo foi lido de novo com outro conteúdo;"
                                    + " o valor não foi somado a nenhuma linha"));
                        } else {
                            avisos.add(new Aviso("REALOCACAO_SEM_EFEITO", "Realocação sem efeito (" + quem + "): a"
                                    + " conta não está em \"a realocar\" no de-para confirmado ou a linha de destino"
                                    + " não recebe débitos"));
                        }
                    });
            return avisos;
        }

        private Regra20 regra20(Apuracao ap, int n, BigDecimal previsto, BigDecimal aRealocar, BigDecimal semLinha,
                boolean provisorio, List<Aviso> avisos) {
            if (e.limiteExcessoPercentual() == null) {
                avisos.add(new Aviso("REGRA_NAO_AVALIADA", "Regra dos 20% (Conv. 16.2) não avaliada: limite não"
                        + " cadastrado para o condomínio"));
                return null;
            }
            List<LinhaExcesso> linhas = new ArrayList<>();
            BigDecimal excesso = ZERO;
            for (LinhaPo l : destinosValidos.values()) {
                BigDecimal dif = ap.realizado(l.getId()).subtract(previsto(l, n));
                if (dif.signum() > 0) {
                    excesso = excesso.add(dif);
                    linhas.add(new LinhaExcesso(l.getId(), l.getCodigoEfetivo(), l.getDescricao(), dif));
                }
            }
            linhas.sort(Comparator.comparing(LinhaExcesso::excesso).reversed().thenComparing(LinhaExcesso::codigo));
            var avaliacao = RegraExcessoMes.avaliar(excesso, previsto, e.limiteExcessoPercentual()).orElse(null);
            if (avaliacao == null) {
                avisos.add(new Aviso("REGRA_NAO_AVALIADA", "Regra dos 20% (Conv. 16.2) não avaliada: previsto do mês"
                        + " sem valor"));
                return null;
            }
            BigDecimal cenario = excesso.add(aRealocar).add(semLinha);
            return new Regra20(RegraExcessoMes.CODIGO, RegraExcessoMes.VERSAO, e.limiteExcessoPercentual(), previsto,
                    excesso, umaCasa(avaliacao.percentual()), avaliacao.limite(), linhas.size(), List.copyOf(linhas),
                    aRealocar, semLinha, cenario, percentual(cenario, previsto), provisorio, avaliacao.acimaDoLimite());
        }

        private List<FundoResultado> fundos(Apuracao ap, int n, List<Aviso> avisos) {
            List<FundoResultado> lista = new ArrayList<>();
            for (LinhaPo l : linhasDeFundo) {
                UUID fundo = e.fundoPorLinha().get(l.getId());
                BigDecimal previsto = previsto(l, n);
                if (fundo == null) {
                    lista.add(new FundoResultado(null, null, l.getId(), l.getCodigoEfetivo(),
                            SituacaoFundo.LINHA_SEM_FUNDO, null, null, null, null, null, null));
                    avisos.add(new Aviso("LINHA_SEM_FUNDO", "linha " + l.getCodigoEfetivo() + " sem fundo ligado"));
                    continue;
                }
                MovimentoFundo m = ap.fundos.getOrDefault(fundo, new MovimentoFundo());
                if (m.reprocessar) {
                    lista.add(new FundoResultado(fundo, nome(fundo), l.getId(), l.getCodigoEfetivo(),
                            SituacaoFundo.REPROCESSAR_FLUXO, previsto, null, null, null, m.creditos, m.debitos));
                    avisos.add(new Aviso("REPROCESSAR_FLUXO", "Fundo " + nome(fundo) + ": reprocesse o fluxo para"
                            + " apurar a arrecadação (recebimento de cota)"));
                    continue;
                }
                lista.add(new FundoResultado(fundo, nome(fundo), l.getId(), l.getCodigoEfetivo(), SituacaoFundo.COMPARADO,
                        previsto, m.arrecadado, m.arrecadado.subtract(previsto), percentual(m.arrecadado, previsto),
                        m.creditos, m.debitos));
            }
            ap.fundos.entrySet().stream().filter(x -> !linhaPorFundo.containsKey(x.getKey()))
                    .sorted(Comparator.comparing(x -> Objects.requireNonNullElse(nome(x.getKey()), "")))
                    .forEach(x -> lista.add(new FundoResultado(x.getKey(), nome(x.getKey()), null, null,
                            SituacaoFundo.SEM_PREVISTO_NA_PO, null, null, null, null, x.getValue().creditos,
                            x.getValue().debitos)));
            return List.copyOf(lista);
        }
    }

    /** Somas de um período (mutável só aqui dentro; o resultado é imutável). */
    private static final class Apuracao {
        final Map<UUID, BigDecimal> porLinha = new HashMap<>();
        final Map<UUID, Integer> qtdPorLinha = new HashMap<>();
        final Acumulador ajustes = new Acumulador();
        final Acumulador aRealocar = new Acumulador();
        final Acumulador semLinha = new Acumulador();
        final Acumulador transferencias = new Acumulador();
        final Map<UUID, MovimentoFundo> fundos = new LinkedHashMap<>();
        final Set<String> contas = new TreeSet<>();
        final Set<String> contasConfirmadas = new TreeSet<>();
        final Set<String> contasSemDepara = new TreeSet<>();
        final Map<String, List<Evidencia>> evidencias = new LinkedHashMap<>();
        final Set<String> realocacoesUsadas = new TreeSet<>();
        BigDecimal debitos = ZERO;
        int qtdDebitos;

        BigDecimal realizado(UUID linha) {
            return porLinha.getOrDefault(linha, ZERO);
        }

        BigDecimal despesa() {
            return porLinha.values().stream().reduce(ZERO, BigDecimal::add).add(aRealocar.total).add(semLinha.total);
        }

        void linha(UUID linha, BigDecimal valor, Lancamento l, Fluxo f, String fundo, String realocacao,
                UUID realocacaoId) {
            porLinha.merge(linha, valor, BigDecimal::add);
            qtdPorLinha.merge(linha, 1, Integer::sum);
            evidencia(alvoLinha(linha), l, f, fundo, realocacao, realocacaoId);
        }

        void evidencia(String alvo, Lancamento l, Fluxo f, String fundo, String realocacao) {
            evidencia(alvo, l, f, fundo, realocacao, null);
        }

        void evidencia(String alvo, Lancamento l, Fluxo f, String fundo, String realocacao, UUID realocacaoId) {
            BigDecimal valor = l.getDebito().signum() != 0 ? l.getDebito() : l.getCredito();
            evidencias.computeIfAbsent(alvo, k -> new ArrayList<>()).add(new Evidencia(l.getId(), l.getData(),
                    l.getContaCodigo(), l.getContaNome(), l.getHistorico(), l.getFornecedor(), l.getDocumento(), valor,
                    fundo, f.arquivoId(), f.nome(), f.sha256(), l.getPagina(), l.getOrdem(), realocacao,
                    realocacaoId));
        }
    }

    private static final class Acumulador {
        final Map<String, ContaBloco> porConta = new TreeMap<>(Comparator.nullsLast(Comparator.naturalOrder()));
        BigDecimal total = ZERO;
        int lancamentos;

        void somar(String conta, String nome, String detalhe, BigDecimal valor) {
            total = total.add(valor);
            lancamentos++;
            porConta.merge(conta, new ContaBloco(conta, nome, detalhe, valor, 1), (a, b) -> new ContaBloco(a.conta(),
                    a.nome() == null ? b.nome() : a.nome(), a.detalhe(), a.valor().add(b.valor()),
                    a.lancamentos() + 1));
        }

        Bloco bloco() {
            return new Bloco(total, lancamentos, List.copyOf(porConta.values()));
        }
    }

    private static final class MovimentoFundo {
        BigDecimal creditos = ZERO;
        BigDecimal debitos = ZERO;
        BigDecimal arrecadado = ZERO;
        boolean reprocessar;
    }

    private static Calculo vazio(String periodo, Situacao situacao, String mensagem, PoResumo po,
            List<MesExercicio> meses, Entrada e) {
        List<Aviso> avisos = new ArrayList<>(e.avisosDaPo());
        return new Calculo(new PrevistoRealizado(VERSAO, periodo, situacao, mensagem, po, List.copyOf(meses), List.of(),
                List.of(), List.of(), null, false, null, List.of(), null, null, null, null, null, List.of(),
                List.copyOf(avisos)), Map.of());
    }

    /** Aviso da prorrogação: no mês prorrogado, "PO prorrogada"; no acumulado, os meses que ficam fora da soma. */
    private static Aviso avisoProrrogacao(PrevisaoOrcamentaria po, VigenciaPo vigencia, VigenciaPo prorrogacao,
            Periodo periodo, boolean mesProrrogado) {
        if (prorrogacao == null) {
            return null;
        }
        String ate = mmaaaa(prorrogacao.fim());
        if (mesProrrogado && periodo instanceof Mes m) {
            return new Aviso("PO_PRORROGADA", "PO prorrogada: " + mmaaaa(m.mes()) + " usa a PO do exercício "
                    + mmaaaa(vigencia.inicio()) + " a " + mmaaaa(vigencia.fim()) + ", prorrogada até " + ate + " por "
                    + po.getProrrogadaPor() + ". Justificativa: " + po.getProrrogacaoJustificativa());
        }
        if (periodo instanceof Acumulado) {
            List<String> prorrogados = meses(prorrogacao.inicio(), prorrogacao.fim()).stream().map(YearMonth::toString)
                    .toList();
            return new Aviso("MESES_PRORROGADOS", "PO prorrogada até " + ate + ": " + listaDeMeses(prorrogados)
                    + " aparece" + (prorrogados.size() == 1 ? "" : "m") + " depois do exercício, marcado"
                    + (prorrogados.size() == 1 ? "" : "s") + " \"prorrogado\", e não entra" + (prorrogados.size() == 1
                    ? "" : "m") + " no acumulado.");
        }
        return null;
    }

    private static Entrada comAviso(Entrada e, Aviso aviso) {
        if (aviso == null) {
            return e;
        }
        List<Aviso> avisos = new ArrayList<>(e.avisosDaPo());
        avisos.add(aviso);
        return new Entrada(e.po(), e.poArquivoNome(), e.linhas(), e.deparas(), e.fundoPorLinha(), e.nomesFundos(),
                e.fundoOrdinarioId(), e.fluxos(), e.lancamentos(), e.realocacoes(), e.limiteExcessoPercentual(), avisos,
                e.periodo());
    }

    private static MesExercicio mesSemNumeros(YearMonth mes, SituacaoMes situacao, List<Fluxo> fluxos) {
        return new MesExercicio(mes.toString(), situacao, fluxos.stream().map(Fluxo::usado).toList(), null, null, null,
                null, null, false);
    }

    static List<YearMonth> meses(YearMonth inicio, YearMonth fim) {
        List<YearMonth> lista = new ArrayList<>();
        for (YearMonth m = inicio; !m.isAfter(fim); m = m.plusMonths(1)) {
            lista.add(m);
        }
        return lista;
    }

    /** Valor ÷ base em %, com 10 casas e exibido com 1 (meio para cima). Base zero: nulo ("—"). */
    static BigDecimal percentual(BigDecimal valor, BigDecimal base) {
        if (base == null || base.signum() == 0) {
            return null;
        }
        return umaCasa(valor.multiply(CEM).divide(base, 10, RoundingMode.HALF_UP));
    }

    static BigDecimal umaCasa(BigDecimal v) {
        return v.setScale(1, RoundingMode.HALF_UP);
    }

    static String mmaaaa(YearMonth m) {
        return "%02d/%d".formatted(m.getMonthValue(), m.getYear());
    }

    /** "mai, jun, jul e ago/2026"; anos diferentes: "nov e dez/2026, jan/2027". */
    static String listaDeMeses(List<String> meses) {
        Map<Integer, List<String>> porAno = new TreeMap<>();
        meses.stream().map(YearMonth::parse).sorted().forEach(m -> porAno.computeIfAbsent(m.getYear(),
                k -> new ArrayList<>()).add(MESES[m.getMonthValue() - 1]));
        return porAno.entrySet().stream().map(x -> juntar(x.getValue()) + "/" + x.getKey())
                .collect(Collectors.joining(", "));
    }

    private static String juntar(List<String> nomes) {
        if (nomes.size() == 1) {
            return nomes.getFirst();
        }
        return String.join(", ", nomes.subList(0, nomes.size() - 1)) + " e " + nomes.getLast();
    }
}
