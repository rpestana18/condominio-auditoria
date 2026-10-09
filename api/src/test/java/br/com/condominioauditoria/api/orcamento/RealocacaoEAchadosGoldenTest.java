package br.com.condominioauditoria.api.orcamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import br.com.condominioauditoria.api.event.BudgetChanged;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.Enrichment;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.LedgerEntryData;
import br.com.condominioauditoria.api.model.accounting.LedgerEntry;
import br.com.condominioauditoria.api.model.audit.Finding;
import br.com.condominioauditoria.api.model.audit.FindingEvent;
import br.com.condominioauditoria.api.model.enums.FindingStatus;
import br.com.condominioauditoria.api.model.enums.Severity;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.Aviso;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.Evidencia;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.LinhaResultado;
import br.com.condominioauditoria.api.orcamento.RealocacaoDtos.PedidoRealocacao;
import br.com.condominioauditoria.api.orcamento.RealocacaoDtos.RealocacaoDto;
import br.com.condominioauditoria.api.service.audit.rule.MonthlyOverrunRule;
import br.com.condominioauditoria.api.service.audit.rule.UnmappedAccountRule;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/**
 * ADR 0004, passo 8, com o caso de aceite de setembro/2026 (golden privado) passando pelos serviços: realocação
 * mínima (RF-03.1.7), chave estável do lançamento (sobrevive ao reprocesso) e recálculo dos achados (RF-03.1.6,
 * RF-03.1.11 e RF-03.1.12, Q27). Pulado sem data/golden/privado.
 */
class RealocacaoEAchadosGoldenTest {

    private static final String SETEMBRO = "2026-09";
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            .withZone(ZoneId.of("America/Sao_Paulo"));

    @Test
    void realocarAsComprasDoCartaoPara179EDesfazerDevolveOValorAnterior() {
        GoldenSetembro g = golden();
        g.confirmarMapa();
        CenarioPo c = g.cenario;
        LinhaResultado antes = linha(consultar(g), "1.7.9");
        List<Evidencia> aRealocar = evidencia(g, CalculoPrevistoRealizado.ALVO_A_REALOCAR);
        assertThat(aRealocar).isNotEmpty().allMatch(ev -> "1064".equals(ev.conta()));
        UUID linha179 = g.linha("1.7.9").getId();

        List<RealocacaoDto> feitas = aRealocar.stream().map(ev -> c.realocacao.realocar(c.condominioId,
                new PedidoRealocacao(ev.lancamentoId(), linha179), "gestor")).toList();
        PrevistoRealizado depois = consultar(g);

        // RF-03.1.7: 1.7.9 a 5.522,25 (+3.222,25); "a realocar" zerado; despesa realizada igual
        assertThat(linha(depois, "1.7.9").realizado()).isEqualByComparingTo("5522.25");
        assertThat(linha(depois, "1.7.9").diferenca()).isEqualByComparingTo("3222.25");
        assertThat(depois.aRealocar().total()).isEqualByComparingTo("0.00");
        assertThat(depois.totais().despesaRealizada()).isEqualByComparingTo("446176.89");
        // O lançamento original continua na conta 1064, com a marca da realocação
        List<Evidencia> da179 = g.cenario.previstoRealizado.evidencia(c.condominioId, SETEMBRO, null,
                CalculoPrevistoRealizado.alvoLinha(linha179));
        String hoje = DATA.format(Instant.now());
        assertThat(da179.stream().filter(ev -> "1064".equals(ev.conta())).toList()).hasSize(aRealocar.size())
                .allSatisfy(ev -> {
                    assertThat(ev.realocacao()).isEqualTo("realocado para 1.7.9 " + g.linha("1.7.9").getDescription()
                            + " por gestor em " + hoje);
                    assertThat(ev.realocacaoId()).isIn(feitas.stream().map(RealocacaoDto::id).toList());
                    assertThat(ev.historico()).isNotBlank();
                });
        assertThat(c.eventosRealocacao).hasSize(aRealocar.size())
                .allMatch(e -> e.getAcao().equals(EventoRealocacao.REALOCADA) && e.getUsuario().equals("gestor"));
        // Realocar de novo o mesmo lançamento é recusado (uma realocação ativa por lançamento)
        assertThatThrownBy(() -> c.realocacao.realocar(c.condominioId, new PedidoRealocacao(
                aRealocar.getFirst().lancamentoId(), linha179), "gestor")).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("já realocado");

        feitas.forEach(r -> c.realocacao.desfazer(c.condominioId, r.id(), "admin"));
        PrevistoRealizado desfeito = consultar(g);

        assertThat(linha(desfeito, "1.7.9").realizado()).isEqualByComparingTo(antes.realizado());
        assertThat(linha(desfeito, "1.7.9").diferenca()).isEqualByComparingTo(antes.diferenca());
        assertThat(desfeito.aRealocar().total()).isEqualByComparingTo("1050.93");
        assertThat(desfeito.totais().despesaRealizada()).isEqualByComparingTo("446176.89");
        // Nada é apagado: as realocações ficam encerradas, com os dois eventos
        assertThat(c.realocacao.listar(c.condominioId, g.po.getId())).hasSize(feitas.size())
                .allSatisfy(r -> {
                    assertThat(r.ativa()).isFalse();
                    assertThat(r.desfeitaPor()).isEqualTo("admin");
                });
        assertThat(c.eventosRealocacao).filteredOn(e -> e.getAcao().equals(EventoRealocacao.DESFEITA))
                .hasSize(feitas.size());
        assertThat(c.publicados).filteredOn(BudgetChanged.class::isInstance).hasSizeGreaterThanOrEqualTo(
                2 * feitas.size());
    }

    @Test
    void soLancamentoARealocarDoFundoCondominioEhRealocado() {
        GoldenSetembro g = golden();
        g.confirmarMapa();
        CenarioPo c = g.cenario;
        Evidencia portaria = evidencia(g, CalculoPrevistoRealizado.alvoLinha(g.linha("1.3.10").getId())).getFirst();

        assertThatThrownBy(() -> c.realocacao.realocar(c.condominioId, new PedidoRealocacao(portaria.lancamentoId(),
                g.linha("1.7.9").getId()), "gestor")).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("a realocar");
        Evidencia cartao = evidencia(g, CalculoPrevistoRealizado.ALVO_A_REALOCAR).getFirst();
        assertThatThrownBy(() -> c.realocacao.realocar(c.condominioId, new PedidoRealocacao(cartao.lancamentoId(),
                g.linha("1.9.1").getId()), "gestor")).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("linha de despesa");
        assertThat(c.realocacoes).isEmpty();
    }

    @Test
    void realocacaoSobreviveAoReprocessoDoMesmoFluxo() {
        GoldenSetembro g = golden();
        g.confirmarMapa();
        CenarioPo c = g.cenario;
        UUID linha179 = g.linha("1.7.9").getId();
        List<Evidencia> aRealocar = evidencia(g, CalculoPrevistoRealizado.ALVO_A_REALOCAR);
        aRealocar.forEach(ev -> c.realocacao.realocar(c.condominioId, new PedidoRealocacao(ev.lancamentoId(),
                linha179), "gestor"));
        Set<UUID> idsAntes = c.lancamentos.stream().map(LedgerEntry::getId).collect(Collectors.toSet());

        g.reprocessarFluxo();
        PrevistoRealizado r = consultar(g);

        // Os lançamentos têm id novo, e a realocação continua valendo pela chave estável
        assertThat(c.lancamentos).noneMatch(l -> idsAntes.contains(l.getId()));
        assertThat(linha(r, "1.7.9").realizado()).isEqualByComparingTo("5522.25");
        assertThat(r.aRealocar().total()).isEqualByComparingTo("0.00");
        assertThat(r.avisos()).extracting(Aviso::codigo).doesNotContain("REALOCACAO_SEM_LANCAMENTO",
                "REALOCACAO_SEM_EFEITO");
        assertThat(evidencia(g, CalculoPrevistoRealizado.alvoLinha(linha179)))
                .filteredOn(ev -> "1064".equals(ev.conta())).hasSize(aRealocar.size())
                .allMatch(ev -> !idsAntes.contains(ev.lancamentoId()) && ev.realocacao() != null);
    }

    @Test
    void realocacaoSemLancamentoCorrespondenteNaoSomaEmSilencio() {
        GoldenSetembro g = golden();
        g.confirmarMapa();
        var orfa = new CalculoPrevistoRealizado.Realocacao(UUID.randomUUID(), "0".repeat(64), g.arquivoFluxo,
                LocalDate.of(2026, 9, 15), "1064", new BigDecimal("99.90"), 7, g.linha("1.7.9").getId(), "gestor",
                Instant.EPOCH);

        PrevistoRealizado r = CalculoPrevistoRealizado.calcular(g.entrada(new CalculoPrevistoRealizado.Mes(
                YearMonth.of(2026, 9)), List.of(g.fluxoDeSetembro()), List.of(orfa))).resultado();

        assertThat(linha(r, "1.7.9").realizado()).isEqualByComparingTo(linha(g.setembro().resultado(), "1.7.9")
                .realizado());
        assertThat(r.aRealocar().total()).isEqualByComparingTo("1050.93");
        assertThat(r.avisos()).filteredOn(a -> a.codigo().equals("REALOCACAO_SEM_LANCAMENTO")).singleElement()
                .satisfies(a -> assertThat(a.texto()).contains("15/09/2026", "conta 1064", "R$ 99,90", "página 7"));
    }

    @Test
    void lancamentoDeTesteSemDeparaGeraUmAchadoAtencaoMesmoComDoisRecalculos() {
        GoldenSetembro g = golden();
        g.confirmarMapa();
        CenarioPo c = g.cenario;
        LedgerEntry teste = lancamentoDeTeste(g, "8888", "CONTA DE TESTE", "500.00");

        c.aposCommit();
        recalcular(c, "primeiro recálculo de teste");
        recalcular(c, "segundo recálculo de teste");

        List<Finding> setembro = achadosDe(c, YearMonth.of(2026, 9));
        assertThat(setembro).singleElement().satisfies(a -> {
            assertThat(a.getRule()).isEqualTo(UnmappedAccountRule.CODE);
            assertThat(a.getRuleVersion()).isEqualTo(UnmappedAccountRule.VERSION);
            assertThat(a.getSeverity()).isEqualTo(Severity.ATENCAO);
            assertThat(a.getTarget()).isEqualTo("conta:8888");
            assertThat(a.getStatus()).isEqualTo(FindingStatus.ABERTO);
            assertThat(a.getDescription()).contains("Conta 8888 CONTA DE TESTE", "09/2026", "R$ 500,00",
                    "1 lançamento", "sem linha da PO", "verificar o de-para");
        });
        Finding achado = setembro.getFirst();
        assertThat(c.evidencias).filteredOn(e -> e.getFindingId().equals(achado.getId())).singleElement()
                .satisfies(e -> {
                    assertThat(e.getFileId()).isEqualTo(g.arquivoFluxo);
                    assertThat(e.getSha256()).isEqualTo(g.arquivo.getSha256());
                    assertThat(e.getPage()).isEqualTo(teste.getPage());
                    assertThat(e.getReference()).contains("30/09/2026", "conta 8888", "R$ 500,00");
                });
        assertThat(eventos(c, achado)).hasSize(1);
        // Nenhum outro achado em setembro: excesso de 8,6%, abaixo dos 20%
        assertThat(c.achados).noneMatch(a -> a.getRule().equals(MonthlyOverrunRule.CODE));
    }

    @Test
    void achadoCujaCondicaoDeixaDeExistirPassaANaoSeAplicaMaisEVoltaSeACondicaoVoltar() {
        GoldenSetembro g = golden();
        g.confirmarMapa();
        CenarioPo c = g.cenario;
        lancamentoDeTeste(g, "8888", "CONTA DE TESTE", "500.00");
        c.aposCommit();
        Finding achado = achadosDe(c, YearMonth.of(2026, 9)).getFirst();

        // Q27: o Admin confirma o de-para da conta; o achado passa a "não se aplica mais", com o motivo
        c.depara.definir(c.condominioId, g.po.getId(), "8888", new DeparaDtos.PedidoDestino(TipoDestino.LINHA_PO,
                g.linha("1.7.8").getId(), null, true), "admin");
        c.aposCommit();

        String hoje = DATA.format(Instant.now());
        assertThat(achado.getStatus()).isEqualTo(FindingStatus.NAO_SE_APLICA_MAIS);
        assertThat(achado.getStatusReason()).isEqualTo("de-para da conta 8888 confirmado por admin em " + hoje);
        assertThat(achado.isConditionPresent()).isFalse();
        assertThat(c.evidencias).filteredOn(e -> e.getFindingId().equals(achado.getId())).hasSize(1);

        // O Admin desfaz o de-para: a condição volta, e o MESMO achado volta a "aberto"
        c.depara.lote(c.condominioId, g.po.getId(), new DeparaDtos.PedidoLote(DeparaDtos.AcaoLote.RECUSAR,
                List.of("8888")), "admin");
        c.aposCommit();

        assertThat(achadosDe(c, YearMonth.of(2026, 9))).singleElement().isSameAs(achado);
        assertThat(achado.getStatus()).isEqualTo(FindingStatus.ABERTO);
        assertThat(eventos(c, achado)).extracting(FindingEvent::getNewStatus).containsExactly(FindingStatus.ABERTO,
                FindingStatus.NAO_SE_APLICA_MAIS, FindingStatus.ABERTO);
        assertThat(eventos(c, achado).get(2).getReason()).isEqualTo("a condição voltou: de-para da conta 8888"
                + " recusado por admin em " + hoje);
    }

    @Test
    void achadoCriticoDos20PorCentoDeixaDeSeAplicarQuandoODeparaDerrubaOExcesso() {
        GoldenSetembro g = golden();
        g.confirmarMapa();
        CenarioPo c = g.cenario;
        // Mês de teste: mais 60.000,00 na portaria (conta 1442 → 1.3.10) leva o excesso a 98.880,19 (21,9%)
        lancamentoDeTeste(g, "1442", "VIGIA E PORTARIA", "60000.00");
        c.aposCommit();

        Finding critico = c.achados.stream().filter(a -> a.getRule().equals(MonthlyOverrunRule.CODE)).findFirst()
                .orElseThrow();
        assertThat(critico.getSeverity()).isEqualTo(Severity.CRITICO);
        assertThat(critico.getReferenceMonth()).isEqualTo(YearMonth.of(2026, 9));
        assertThat(critico.getDescription()).startsWith("excesso de 21,9% do previsto do mês; a Conv. 16.2 exige"
                + " aprovação em AGE para o excedente; verificar ata.").contains("R$ 98.880,19", "R$ 90.324,03");
        assertThat(c.evidencias).filteredOn(e -> e.getFindingId().equals(critico.getId()))
                .anyMatch(e -> e.getBudgetLineId() != null && e.getReference().startsWith("PO, linha 1.3.10"))
                .anyMatch(e -> e.getFileId().equals(g.arquivoFluxo));

        // A conta 1442 vira ajuste no de-para: o excesso cai abaixo de 20% e o achado não se aplica mais
        c.depara.definir(c.condominioId, g.po.getId(), "1442", new DeparaDtos.PedidoDestino(TipoDestino.AJUSTE, null,
                "teste", true), "admin");
        c.aposCommit();

        assertThat(critico.getStatus()).isEqualTo(FindingStatus.NAO_SE_APLICA_MAIS);
        assertThat(critico.getStatusReason()).startsWith("de-para da conta 1442 confirmado por admin em ");
        assertThat(c.achados).filteredOn(a -> a.getRule().equals(MonthlyOverrunRule.CODE)).hasSize(1);
    }

    @Test
    void mesComDoisFluxosNaoMudaAchados() {
        GoldenSetembro g = golden();
        g.confirmarMapa();
        CenarioPo c = g.cenario;
        lancamentoDeTeste(g, "8888", "CONTA DE TESTE", "500.00");
        c.aposCommit();
        Finding achado = achadosDe(c, YearMonth.of(2026, 9)).getFirst();

        c.fluxo("fluxo-corrigido-2026-09.pdf", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 0);
        c.depara.definir(c.condominioId, g.po.getId(), "8888", new DeparaDtos.PedidoDestino(TipoDestino.LINHA_PO,
                g.linha("1.7.8").getId(), null, true), "admin");
        c.aposCommit();

        assertThat(achado.getStatus()).isEqualTo(FindingStatus.ABERTO);
        assertThat(eventos(c, achado)).hasSize(1);
    }

    private static LedgerEntry lancamentoDeTeste(GoldenSetembro g, String conta, String nome, String valor) {
        var lido = new LedgerEntryData(99, 9000 + g.cenario.lancamentos.size(), LocalDate.of(2026, 9, 30), conta, nome,
                "", "Lançamento de teste", BigDecimal.ZERO.setScale(2), new BigDecimal(valor),
                BigDecimal.ZERO.setScale(2), new Enrichment(null, null, null, false, false));
        LedgerEntry l = new LedgerEntry(g.cenario.condominioId, g.arquivoFluxo, g.cenario.ordinario.getId(), lido);
        g.cenario.lancamentos.add(l);
        return l;
    }

    private static void recalcular(CenarioPo c, String descricao) {
        c.recalculo.recalcular(BudgetChanged.of(c.condominioId, descricao, "sistema", Instant.now()));
    }

    private static List<Finding> achadosDe(CenarioPo c, YearMonth mes) {
        return c.achados.stream().filter(a -> a.getReferenceMonth().equals(mes)).toList();
    }

    private static List<FindingEvent> eventos(CenarioPo c, Finding a) {
        return c.eventosAchado.stream().filter(e -> e.getFindingId().equals(a.getId())).toList();
    }

    private static PrevistoRealizado consultar(GoldenSetembro g) {
        return g.cenario.previstoRealizado.consultar(g.cenario.condominioId, SETEMBRO, null);
    }

    private static List<Evidencia> evidencia(GoldenSetembro g, String alvo) {
        return g.cenario.previstoRealizado.evidencia(g.cenario.condominioId, SETEMBRO, null, alvo);
    }

    private static LinhaResultado linha(PrevistoRealizado r, String codigo) {
        return r.grupos().stream().flatMap(gr -> gr.linhas().stream()).filter(l -> l.codigo().equals(codigo))
                .findFirst().orElseThrow();
    }

    private static GoldenSetembro golden() {
        Optional<GoldenSetembro> g = GoldenSetembro.carregar();
        assumeTrue(g.isPresent() && GoldenSetembro.mapa().isPresent(), "golden privado ausente");
        return g.get();
    }
}
