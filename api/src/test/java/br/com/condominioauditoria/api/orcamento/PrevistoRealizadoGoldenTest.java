package br.com.condominioauditoria.api.orcamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import br.com.condominioauditoria.api.messaging.GoldenMessages;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.Enrichment;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.LedgerEntryData;
import br.com.condominioauditoria.api.model.accounting.LedgerEntry;
import br.com.condominioauditoria.api.orcamento.CalculoPrevistoRealizado.Acumulado;
import br.com.condominioauditoria.api.orcamento.CalculoPrevistoRealizado.Mes;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.FundoResultado;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.GrupoResultado;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.LinhaResultado;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.Situacao;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.SituacaoFundo;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Caso de aceite do RF-03.1.15: setembro/2026 do piloto, com a PO e o fluxo reais (mensagens v2 do golden privado), o
 * de-para das 73 contas confirmado e os fundos ligados. Cada linha de despesa do previsto-realizado-2026-09.csv é
 * conferida centavo a centavo. Pulado sem data/golden/privado.
 */
class PrevistoRealizadoGoldenTest {

    @Test
    void setembroFundoCondominio() {
        GoldenSetembro g = golden();
        g.confirmarMapa();

        PrevistoRealizado r = g.setembro().resultado();

        assertThat(r.situacao()).isEqualTo(Situacao.CALCULADO);
        assertThat(r.versaoCalculo()).isEqualTo(CalculoPrevistoRealizado.VERSAO);
        // RF-03.1.6
        assertThat(r.conferencia().debitosDoFundo()).isEqualByComparingTo("449455.13");
        assertThat(r.conferencia().confere()).isTrue();
        assertThat(r.ajustes().total()).isEqualByComparingTo("3278.24");
        assertThat(r.ajustes().contas()).extracting(c -> c.conta() + "=" + c.valor().toPlainString())
                .containsExactly("1324=3987.12", "1327=-708.88");
        assertThat(r.aRealocar().total()).isEqualByComparingTo("1050.93");
        assertThat(r.aRealocar().contas()).singleElement().satisfies(c -> assertThat(c.conta()).isEqualTo("1064"));
        assertThat(r.totais().despesaRealizada()).isEqualByComparingTo("446176.89");
        assertThat(r.totais().emLinhas()).isEqualByComparingTo("445125.96");
        assertThat(r.totais().previsto()).isEqualByComparingTo("451620.13");
        assertThat(r.totais().previstoMes()).isEqualByComparingTo("451620.13");
        assertThat(r.totais().diferenca()).isEqualByComparingTo("-5443.24");
        assertThat(r.totais().execucao()).isEqualByComparingTo("98.8");
        assertThat(r.semLinhaPo().total()).isEqualByComparingTo("0.00");
        assertThat(r.semLinhaPo().lancamentos()).isZero();
        assertThat(r.depara()).isEqualTo(new PrevistoRealizado.ResumoDeparaPeriodo(73, 73, 0));
        assertThat(r.provisorio()).isTrue();

        // Grupos (previsto; realizado; diferença). Contratos pela soma das linhas (Q30): 336.274,18
        assertThat(grupos(r)).containsExactly(
                "1.1 69193.86 62815.41 -6378.45", "1.2 694.05 754.94 60.89", "1.3 336274.18 341277.13 5002.95",
                "1.4 0.00 0.00 0.00", "1.5 2850.00 8958.32 6108.32", "1.6 17388.04 14264.32 -3123.72",
                "1.7 15200.00 16910.40 1710.40", "1.8 10020.00 145.44 -9874.56");

        // RF-03.1.8 e RF-03.1.12
        LinhaResultado sindico = linha(r, "1.3.20");
        assertThat(List.of(sindico.previsto(), sindico.realizado(), sindico.diferenca()))
                .usingElementComparator(BigDecimal::compareTo).containsExactly(dec("8000.00"), dec("7120.00"),
                        dec("-880.00"));
        var evidenciaSindico = g.setembro().evidencias().get(CalculoPrevistoRealizado.alvoLinha(sindico.linhaId()));
        assertThat(evidenciaSindico).singleElement().satisfies(ev -> {
            assertThat(ev.conta()).isEqualTo("1108");
            assertThat(ev.data()).isEqualTo(LocalDate.of(2026, 9, 9));
            assertThat(ev.pagina()).isPositive();
            assertThat(ev.arquivoId()).isEqualTo(g.arquivoFluxo);
            assertThat(ev.sha256()).hasSize(64);
        });
        LinhaResultado portaria = linha(r, "1.3.10");
        assertThat(portaria.realizado()).isEqualByComparingTo("86816.34");
        assertThat(portaria.execucao()).isEqualByComparingTo("108.5");
        var evidenciaPortaria = g.setembro().evidencias().get(CalculoPrevistoRealizado.alvoLinha(portaria.linhaId()));
        assertThat(evidenciaPortaria).allMatch(ev -> ev.conta().equals("1442"));
        assertThat(evidenciaPortaria.stream().map(PrevistoRealizado.Evidencia::valor).reduce(BigDecimal.ZERO,
                BigDecimal::add)).isEqualByComparingTo("86816.34");
        assertThat(linha(r, "1.1.1").diferenca()).isEqualByComparingTo("-9556.62");
        assertThat(linha(r, "1.1.5").realizado()).isEqualByComparingTo("0.00");
        assertThat(linha(r, "1.3.23").realizado()).isEqualByComparingTo("0.00");
    }

    @Test
    void cadaLinhaDoCsvCentavoACentavo() {
        GoldenSetembro g = golden();
        Optional<String> csv = GoldenMessages.text("previsto-realizado-2026-09.csv");
        assumeTrue(csv.isPresent(), "previsto-realizado-2026-09.csv ausente no golden privado");
        g.confirmarMapa();
        PrevistoRealizado r = g.setembro().resultado();
        Map<String, LinhaResultado> porCodigo = r.grupos().stream().flatMap(gr -> gr.linhas().stream())
                .collect(Collectors.toMap(LinhaResultado::codigo, l -> l));

        int conferidas = 0;
        Map<String, String[]> especiais = new HashMap<>();
        for (String linha : csv.get().lines().skip(1).toList()) {
            String[] c = linha.split(";", -1);
            String codigo = c[0];
            if (!codigo.matches("\\d+(\\.\\d+)+")) {
                especiais.put(codigo, c);
                continue;
            }
            if (codigo.startsWith("1.9.")) {
                continue; // fundos: comparados pela arrecadação (RF-03.1.9), no teste dos fundos
            }
            LinhaResultado l = porCodigo.get(codigo);
            assertThat(l).as("linha %s existe no resultado", codigo).isNotNull();
            assertThat(l.previsto()).as("previsto %s", codigo).isEqualByComparingTo(br(c[2]));
            assertThat(l.realizado()).as("realizado %s", codigo).isEqualByComparingTo(br(c[3]));
            assertThat(l.diferenca()).as("diferença %s", codigo).isEqualByComparingTo(br(c[4]));
            List<String> contas = c[5].isBlank() ? List.of() : Arrays.asList(c[5].trim().split(" "));
            assertThat(l.contasFluxo()).as("contas do fluxo %s", codigo).containsExactlyInAnyOrderElementsOf(contas);
            conferidas++;
        }
        assertThat(conferidas).isEqualTo(70);
        // Linhas da PO que o CSV não lista: previsto e realizado zero
        porCodigo.values().stream().filter(l -> csv.get().lines().noneMatch(x -> x.startsWith(l.codigo() + ";")))
                .forEach(l -> {
                    assertThat(l.previsto()).as("previsto %s fora do CSV", l.codigo()).isEqualByComparingTo("0");
                    assertThat(l.realizado()).as("realizado %s fora do CSV", l.codigo()).isEqualByComparingTo("0");
                });
        // Blocos à parte do CSV: ajustes e a realocar
        assertThat(br(especiais.get("AJUSTE (estorno)")[3]).add(br(especiais.get("AJUSTE (repasse)")[3])))
                .isEqualByComparingTo(r.ajustes().total());
        assertThat(br(especiais.get("REALOCAR (cartão)")[3])).isEqualByComparingTo(r.aRealocar().total());
    }

    @Test
    void regraDos20PorCentoEmSetembro() {
        GoldenSetembro g = golden();
        g.confirmarMapa();

        var regra = g.setembro().resultado().regra20();

        assertThat(regra.excesso()).isEqualByComparingTo("38880.19");
        assertThat(regra.linhasAcima()).isEqualTo(25);
        assertThat(regra.percentual()).isEqualByComparingTo("8.6");
        assertThat(regra.previstoMes()).isEqualByComparingTo("451620.13");
        assertThat(regra.limite()).isEqualByComparingTo("90324.03");
        assertThat(regra.acimaDoLimite()).isFalse();
        assertThat(regra.aRealocar()).isEqualByComparingTo("1050.93");
        assertThat(regra.cenarioMaximo()).isEqualByComparingTo("39931.12");
        assertThat(regra.percentualCenarioMaximo()).isEqualByComparingTo("8.8");
        assertThat(regra.provisorio()).isTrue();
        assertThat(regra.linhas().stream().map(PrevistoRealizado.LinhaExcesso::excesso).reduce(BigDecimal.ZERO,
                BigDecimal::add)).isEqualByComparingTo("38880.19");
    }

    @Test
    void fundosDeReservaEObrasPelaArrecadacao() {
        GoldenSetembro g = golden();
        g.confirmarMapa();

        List<FundoResultado> fundos = g.setembro().resultado().fundos();

        FundoResultado reserva = fundo(fundos, "FUNDO DE RESERVA");
        assertThat(reserva.situacao()).isEqualTo(SituacaoFundo.COMPARADO);
        assertThat(reserva.linhaCodigo()).isEqualTo("1.9.1");
        assertThat(List.of(reserva.previsto(), reserva.arrecadado(), reserva.diferenca(), reserva.execucao()))
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(dec("13548.60"), dec("14260.79"), dec("712.19"), dec("105.3"));
        FundoResultado obras = fundo(fundos, "OBRAS / REFORMAS / INFRA");
        assertThat(obras.linhaCodigo()).isEqualTo("1.9.2");
        assertThat(List.of(obras.previsto(), obras.arrecadado(), obras.diferenca(), obras.execucao()))
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(dec("9032.40"), dec("9705.06"), dec("672.66"), dec("107.4"));
        FundoResultado energia = fundo(fundos, "ENERGIA ELETRICA");
        assertThat(energia.situacao()).isEqualTo(SituacaoFundo.SEM_PREVISTO_NA_PO);
        assertThat(energia.diferenca()).isNull();
        FundoResultado soObras = fundo(fundos, "OBRAS");
        assertThat(soObras.situacao()).isEqualTo(SituacaoFundo.SEM_PREVISTO_NA_PO);
        assertThat(soObras.debitos()).isEqualByComparingTo("25.13");
    }

    @Test
    void acumuladoComSoSetembro() {
        GoldenSetembro g = golden();
        g.confirmarMapa();

        PrevistoRealizado r = CalculoPrevistoRealizado.calcular(g.entrada(new Acumulado(), List.of(g.fluxoDeSetembro()),
                List.of())).resultado();

        assertThat(r.situacao()).isEqualTo(Situacao.CALCULADO);
        assertThat(r.totais().previsto()).isEqualByComparingTo("451620.13");
        assertThat(r.totais().despesaRealizada()).isEqualByComparingTo("446176.89");
        assertThat(r.totais().previstoExercicio()).isEqualByComparingTo("5419441.56");
        assertThat(r.mesesSomados()).containsExactly("2026-09");
        assertThat(r.mesesSemFluxo()).containsExactly("2026-05", "2026-06", "2026-07", "2026-08");
        assertThat(r.avisos()).extracting(PrevistoRealizado.Aviso::texto)
                .contains("mai, jun, jul e ago/2026 sem fluxo carregado");
        assertThat(r.meses()).hasSize(12);
        assertThat(r.meses().get(4).situacao()).isEqualTo(PrevistoRealizado.SituacaoMes.COM_FLUXO);
        assertThat(r.meses().get(4).excesso()).isEqualByComparingTo("38880.19");
        assertThat(r.meses().get(0).previsto()).isNull();
        assertThat(r.regra20()).isNull();
    }

    @Test
    void as73ContasSugeridasNaoSomamNada() {
        GoldenSetembro g = golden();
        g.cenario.depara.loadSheet(g.cenario.condominioId, g.po.getId(), "mapa.csv", GoldenSetembro.mapa()
                .orElseThrow(), "admin");

        PrevistoRealizado r = g.setembro().resultado();

        assertThat(r.totais().emLinhas()).isEqualByComparingTo("0.00");
        assertThat(r.grupos()).flatMap(GrupoResultado::linhas)
                .allMatch(l -> l.realizado().signum() == 0 && l.contasFluxo().isEmpty());
        assertThat(r.semLinhaPo().total()).isEqualByComparingTo("449455.13");
        assertThat(r.semLinhaPo().lancamentos()).isEqualTo(r.conferencia().lancamentos());
        assertThat(r.ajustes().total()).isEqualByComparingTo("0.00");
        assertThat(r.aRealocar().total()).isEqualByComparingTo("0.00");
        assertThat(r.depara()).isEqualTo(new PrevistoRealizado.ResumoDeparaPeriodo(73, 0, 73));
        assertThat(r.avisos()).extracting(PrevistoRealizado.Aviso::texto)
                .anyMatch(t -> t.startsWith("73 contas sem de-para confirmado"));
        assertThat(r.provisorio()).isTrue();
        assertThat(r.semLinhaPo().contas()).allMatch(c -> c.detalhe().equals("de-para sugerido"));
    }

    @Test
    void lancamentoDeTesteSemDeparaFicaEmSemLinhaDaPo() {
        GoldenSetembro g = golden();
        g.confirmarMapa();
        var lido = new LedgerEntryData(99, 1, LocalDate.of(2026, 9, 30), "8888", "CONTA DE TESTE", "", "Teste",
                BigDecimal.ZERO.setScale(2), new BigDecimal("500.00"), BigDecimal.ZERO.setScale(2),
                new Enrichment(null, null, null, false, false));
        g.cenario.lancamentos.add(new LedgerEntry(g.cenario.condominioId, g.arquivoFluxo, g.cenario.ordinario.getId(),
                lido));

        PrevistoRealizado r = g.setembro().resultado();

        assertThat(r.semLinhaPo().total()).isEqualByComparingTo("500.00");
        assertThat(r.semLinhaPo().contas()).singleElement().satisfies(c -> {
            assertThat(c.conta()).isEqualTo("8888");
            assertThat(c.detalhe()).isEqualTo("sem de-para");
        });
        assertThat(r.totais().despesaRealizada()).isEqualByComparingTo("446676.89");
        assertThat(r.totais().emLinhas()).isEqualByComparingTo("445125.96");
        assertThat(r.regra20().excesso()).isEqualByComparingTo("38880.19");
        assertThat(r.avisos()).extracting(PrevistoRealizado.Aviso::texto)
                .anyMatch(t -> t.startsWith("1 conta sem de-para confirmado"));
    }

    @Test
    void realocacaoDasComprasDoCartaoPara179() {
        GoldenSetembro g = golden();
        g.confirmarMapa();
        List<CalculoPrevistoRealizado.Realocacao> realocacoes = g.cenario.lancamentos.stream()
                .filter(l -> "1064".equals(l.getAccountCode()) && l.getDebit().signum() != 0)
                .map(l -> new CalculoPrevistoRealizado.Realocacao(UUID.randomUUID(),
                        br.com.condominioauditoria.api.model.accounting.LedgerEntryFingerprint.key(l), l.getFileId(),
                        l.getDate(), l.getAccountCode(), l.getDebit(), l.getPage(), g.linha("1.7.9").getId(),
                        "gestor", Instant.EPOCH))
                .toList();

        PrevistoRealizado r = CalculoPrevistoRealizado.calcular(g.entrada(new Mes(YearMonth.of(2026, 9)),
                List.of(g.fluxoDeSetembro()), realocacoes)).resultado();

        assertThat(linha(r, "1.7.9").realizado()).isEqualByComparingTo("5522.25");
        assertThat(linha(r, "1.7.9").diferenca()).isEqualByComparingTo("3222.25");
        assertThat(r.aRealocar().total()).isEqualByComparingTo("0.00");
        assertThat(r.totais().despesaRealizada()).isEqualByComparingTo("446176.89");
    }

    @Test
    void doisFluxosNoMesNaoMostramNumero() {
        GoldenSetembro g = golden();
        g.confirmarMapa();
        var copia = new CalculoPrevistoRealizado.Fluxo(UUID.randomUUID(), "fluxo-corrigido.pdf", "e".repeat(64),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), Instant.EPOCH.plusSeconds(60), "gestor");

        PrevistoRealizado mes = CalculoPrevistoRealizado.calcular(g.entrada(new Mes(YearMonth.of(2026, 9)),
                List.of(g.fluxoDeSetembro(), copia), List.of())).resultado();
        PrevistoRealizado acumulado = CalculoPrevistoRealizado.calcular(g.entrada(new Acumulado(),
                List.of(g.fluxoDeSetembro(), copia), List.of())).resultado();

        assertThat(mes.situacao()).isEqualTo(Situacao.DOIS_FLUXOS);
        assertThat(mes.mensagem()).isEqualTo("Dois fluxos para 09/2026: substitua, reclassifique ou exclua um");
        assertThat(mes.totais()).isNull();
        assertThat(mes.grupos()).isEmpty();
        assertThat(mes.meses().getFirst().fluxos()).hasSize(2);
        assertThat(acumulado.situacao()).isEqualTo(Situacao.DOIS_FLUXOS);
        assertThat(acumulado.mesesComDoisFluxos()).containsExactly("2026-09");
    }

    @Test
    void mesmoInsumoMesmoResultado() {
        GoldenSetembro g = golden();
        g.confirmarMapa();

        assertThat(g.setembro()).isEqualTo(g.setembro());
    }

    private static GoldenSetembro golden() {
        Optional<GoldenSetembro> g = GoldenSetembro.carregar();
        assumeTrue(g.isPresent() && GoldenSetembro.mapa().isPresent(), "golden privado ausente");
        return g.get();
    }

    private static List<String> grupos(PrevistoRealizado r) {
        return r.grupos().stream().map(gr -> gr.codigo() + " " + gr.previsto().toPlainString() + " "
                + gr.realizado().toPlainString() + " " + gr.diferenca().toPlainString()).toList();
    }

    private static LinhaResultado linha(PrevistoRealizado r, String codigo) {
        return r.grupos().stream().flatMap(g -> g.linhas().stream()).filter(l -> l.codigo().equals(codigo)).findFirst()
                .orElseThrow();
    }

    private static FundoResultado fundo(List<FundoResultado> fundos, String nome) {
        return fundos.stream().filter(f -> nome.equals(f.fundo())).findFirst().orElseThrow();
    }

    private static BigDecimal br(String texto) {
        return new BigDecimal(texto.trim().replace(".", "").replace(',', '.'));
    }

    private static BigDecimal dec(String v) {
        return new BigDecimal(v);
    }
}
