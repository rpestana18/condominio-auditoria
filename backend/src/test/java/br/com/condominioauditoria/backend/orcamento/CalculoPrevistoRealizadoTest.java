package br.com.condominioauditoria.backend.orcamento;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.backend.contabil.Lancamento;
import br.com.condominioauditoria.backend.mensagens.ResultadoProcessamento.Enriquecimento;
import br.com.condominioauditoria.backend.mensagens.ResultadoProcessamento.LancamentoLido;
import br.com.condominioauditoria.backend.orcamento.CalculoPrevistoRealizado.Acumulado;
import br.com.condominioauditoria.backend.orcamento.CalculoPrevistoRealizado.Entrada;
import br.com.condominioauditoria.backend.orcamento.CalculoPrevistoRealizado.Fluxo;
import br.com.condominioauditoria.backend.orcamento.CalculoPrevistoRealizado.Mes;
import br.com.condominioauditoria.backend.orcamento.CalculoPrevistoRealizado.Periodo;
import br.com.condominioauditoria.backend.orcamento.PrevistoRealizado.Situacao;
import br.com.condominioauditoria.backend.orcamento.PrevistoRealizado.SituacaoFundo;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** RF-03.1.6 a RF-03.1.11 com dados sintéticos (sem o golden): bordas e situações sem número. */
class CalculoPrevistoRealizadoTest {

    private static final UUID CONDOMINIO = UUID.randomUUID();
    private static final UUID ORDINARIO = UUID.randomUUID();
    private static final UUID RESERVA = UUID.randomUUID();
    private static final YearMonth SET = YearMonth.of(2026, 9);

    private final List<Lancamento> lancamentos = new ArrayList<>();
    private final UUID arquivoSet = UUID.randomUUID();
    private final UUID arquivoOut = UUID.randomUUID();

    @Test
    void exatamente20PorCentoNaoUltrapassaEUmCentavoAMaisUltrapassa() {
        // Previsto 451.620,10; excesso 90.324,02 = exatamente 20% → sem achado ("não ultrapassem 20%")
        Po po = poSimples("451620.10");
        debito(po, arquivoSet, "1001", "541944.12", LocalDate.of(2026, 9, 5));

        var r = calcular(po, new Mes(SET), List.of(fluxo(arquivoSet, SET))).regra20();

        assertThat(r.excesso()).isEqualByComparingTo("90324.02");
        assertThat(r.limite()).isEqualByComparingTo("90324.02");
        assertThat(r.percentual()).isEqualByComparingTo("20.0");
        assertThat(r.acimaDoLimite()).isFalse();

        lancamentos.clear();
        debito(po, arquivoSet, "1001", "541944.13", LocalDate.of(2026, 9, 5));
        var acima = calcular(po, new Mes(SET), List.of(fluxo(arquivoSet, SET))).regra20();
        assertThat(acima.excesso()).isEqualByComparingTo("90324.03");
        assertThat(acima.acimaDoLimite()).isTrue();
        assertThat(acima.provisorio()).isFalse();
    }

    @Test
    void mesSemFluxoNuncaViraZero() {
        Po po = poSimples("1000.00");

        var r = calcular(po, new Mes(SET), List.of());

        assertThat(r.situacao()).isEqualTo(Situacao.SEM_FLUXO);
        assertThat(r.mensagem()).isEqualTo("Sem fluxo carregado para 09/2026");
        assertThat(r.totais()).isNull();
        assertThat(r.grupos()).isEmpty();
    }

    @Test
    void foraDoExercicioSemPoNaoConfirmadaESemFundoOrdinario() {
        Po po = poSimples("1000.00");
        assertThat(calcular(po, new Mes(YearMonth.of(2026, 4)), List.of()).mensagem())
                .isEqualTo("Sem PO aprovada para 04/2026");
        assertThat(CalculoPrevistoRealizado.calcular(entrada(null, new Mes(SET), List.of(), ORDINARIO)).resultado()
                .situacao()).isEqualTo(Situacao.SEM_PO);

        PrevisaoOrcamentaria lida = new PrevisaoOrcamentaria(CONDOMINIO, UUID.randomUUID(), "a".repeat(64));
        lida.registrarLeitura("po-protest", "PO", null, null, null, EstadoPrevisao.LIDA, null, null, BigDecimal.ONE,
                new BigDecimal("0.01"), Instant.EPOCH);
        var naoConfirmada = CalculoPrevistoRealizado.calcular(new Entrada(lida, null, po.linhas, List.of(), Map.of(),
                Map.of(), ORDINARIO, List.of(), List.of(), List.of(), null, List.of(), new Mes(SET))).resultado();
        assertThat(naoConfirmada.situacao()).isEqualTo(Situacao.PO_NAO_CONFIRMADA);
        assertThat(naoConfirmada.totais()).isNull();

        var semOrdinario = CalculoPrevistoRealizado.calcular(entrada(po, new Mes(SET), List.of(fluxo(arquivoSet, SET)),
                null)).resultado();
        assertThat(semOrdinario.situacao()).isEqualTo(Situacao.SEM_FUNDO_ORDINARIO);
    }

    @Test
    void transferenciaEntreFundosFicaForaEAConferenciaFecha() {
        Po po = poSimples("1000.00");
        debito(po, arquivoSet, "1001", "800.00", LocalDate.of(2026, 9, 5));
        Lancamento transf = lancamento(arquivoSet, ORDINARIO, "2133", "0.00", "50.00", true, false,
                LocalDate.of(2026, 9, 6));
        lancamentos.add(transf);
        debito(po, arquivoSet, null, "30.00", LocalDate.of(2026, 9, 7));

        var r = calcular(po, new Mes(SET), List.of(fluxo(arquivoSet, SET)));

        assertThat(r.totais().emLinhas()).isEqualByComparingTo("800.00");
        assertThat(r.semLinhaPo().total()).isEqualByComparingTo("30.00");
        assertThat(r.semLinhaPo().contas()).singleElement().satisfies(c -> assertThat(c.detalhe())
                .isEqualTo("lançamento sem conta"));
        assertThat(r.totais().despesaRealizada()).isEqualByComparingTo("830.00");
        assertThat(r.conferencia().debitosDoFundo()).isEqualByComparingTo("880.00");
        assertThat(r.conferencia().transferencias()).isEqualByComparingTo("50.00");
        assertThat(r.conferencia().confere()).isTrue();
        assertThat(r.totais().execucao()).isEqualByComparingTo("83.0");
    }

    @Test
    void lancamentoDeOutroMesOuDeOutroArquivoNaoEntra() {
        Po po = poSimples("1000.00");
        debito(po, arquivoSet, "1001", "100.00", LocalDate.of(2026, 9, 30));
        debito(po, arquivoSet, "1001", "200.00", LocalDate.of(2026, 10, 1));
        debito(po, UUID.randomUUID(), "1001", "400.00", LocalDate.of(2026, 9, 15));

        var r = calcular(po, new Mes(SET), List.of(fluxo(arquivoSet, SET)));

        assertThat(r.totais().despesaRealizada()).isEqualByComparingTo("100.00");
    }

    @Test
    void acumuladoSomaSoOsMesesComFluxo() {
        Po po = poSimples("1000.00");
        debito(po, arquivoSet, "1001", "900.00", LocalDate.of(2026, 9, 10));
        debito(po, arquivoOut, "1001", "1300.00", LocalDate.of(2026, 10, 10));

        var r = calcular(po, new Acumulado(), List.of(fluxo(arquivoSet, SET), fluxo(arquivoOut, SET.plusMonths(1))));

        assertThat(r.mesesSomados()).containsExactly("2026-09", "2026-10");
        assertThat(r.mesesSemFluxo()).containsExactly("2026-05", "2026-06", "2026-07", "2026-08");
        assertThat(r.totais().previsto()).isEqualByComparingTo("2000.00");
        assertThat(r.totais().despesaRealizada()).isEqualByComparingTo("2200.00");
        assertThat(r.totais().previstoExercicio()).isEqualByComparingTo("12000.00");
        assertThat(r.meses()).hasSize(12);
        assertThat(r.meses().get(5).excesso()).isEqualByComparingTo("300.00");
        assertThat(r.regra20()).isNull();
    }

    @Test
    void fundosSemRecebimentoDeCotaGravadoPedemReprocessoELinhaSemFundoNaoTemNumero() throws Exception {
        Po po = poComFundo();
        Lancamento cota = lancamento(arquivoSet, RESERVA, null, "300.00", "0.00", false, true, LocalDate.of(2026, 9, 2));
        Lancamento rendimento = lancamento(arquivoSet, RESERVA, null, "100.00", "0.00", false, false,
                LocalDate.of(2026, 9, 3));
        lancamentos.addAll(List.of(cota, rendimento));

        var r = calcular(po, new Mes(SET), List.of(fluxo(arquivoSet, SET)));
        var reserva = r.fundos().stream().filter(f -> RESERVA.equals(f.fundoId())).findFirst().orElseThrow();
        assertThat(reserva.situacao()).isEqualTo(SituacaoFundo.COMPARADO);
        assertThat(reserva.arrecadado()).isEqualByComparingTo("300.00");
        assertThat(reserva.creditos()).isEqualByComparingTo("400.00");
        assertThat(r.fundos()).anySatisfy(f -> {
            assertThat(f.situacao()).isEqualTo(SituacaoFundo.LINHA_SEM_FUNDO);
            assertThat(f.linhaCodigo()).isEqualTo("1.9.2");
            assertThat(f.arrecadado()).isNull();
        });
        assertThat(r.avisos()).extracting(PrevistoRealizado.Aviso::texto).contains("linha 1.9.2 sem fundo ligado");

        // Fluxo gravado antes da coluna recebimento_cota (V9): nunca zero, pede reprocesso
        var campo = Lancamento.class.getDeclaredField("recebimentoCota");
        campo.setAccessible(true);
        campo.set(cota, null);
        var antigo = calcular(po, new Mes(SET), List.of(fluxo(arquivoSet, SET)));
        assertThat(antigo.fundos()).anySatisfy(f -> {
            assertThat(f.situacao()).isEqualTo(SituacaoFundo.REPROCESSAR_FLUXO);
            assertThat(f.arrecadado()).isNull();
        });
    }

    @Test
    void percentualComPrevistoZeroFicaVazioEListaDeMeses() {
        assertThat(CalculoPrevistoRealizado.percentual(new BigDecimal("10.00"), BigDecimal.ZERO)).isNull();
        assertThat(CalculoPrevistoRealizado.percentual(new BigDecimal("38880.19"), new BigDecimal("451620.13")))
                .isEqualByComparingTo("8.6");
        assertThat(CalculoPrevistoRealizado.listaDeMeses(List.of("2026-05", "2026-06", "2026-07", "2026-08")))
                .isEqualTo("mai, jun, jul e ago/2026");
        assertThat(CalculoPrevistoRealizado.listaDeMeses(List.of("2026-11", "2026-12", "2027-01")))
                .isEqualTo("nov e dez/2026, jan/2027");
    }

    // ---- montagem

    /** PO confirmada 05/2026 a 04/2027 com um grupo de uma linha (1.1.1) e de-para 1001 → 1.1.1 confirmado. */
    private record Po(PrevisaoOrcamentaria previsao, List<LinhaPo> linhas, List<DeparaConta> deparas,
            Map<UUID, UUID> fundoPorLinha) {
    }

    private static Po poSimples(String orcado) {
        PrevisaoOrcamentaria p = confirmada();
        LinhaPo total = linha(p, 1, TipoLinhaPo.TOTAL, "1", null, "TOTAL", orcado);
        LinhaPo grupo = linha(p, 2, TipoLinhaPo.GRUPO, "1.1", null, "PESSOAL", orcado);
        LinhaPo l = linha(p, 3, TipoLinhaPo.LINHA, "1.1.1", "1545 - Salários", "Salários", orcado);
        DeparaConta d = new DeparaConta(p, "1001", "SALARIO", Destino.linha(l), EstadoDepara.CONFIRMADO,
                OrigemDepara.ADMIN, null, false, "admin", Instant.EPOCH);
        return new Po(p, List.of(total, grupo, l), List.of(d), Map.of());
    }

    private static Po poComFundo() {
        PrevisaoOrcamentaria p = confirmada();
        LinhaPo grupo = linha(p, 1, TipoLinhaPo.GRUPO, "1.1", null, "PESSOAL", "1000.00");
        LinhaPo l = linha(p, 2, TipoLinhaPo.LINHA, "1.1.1", "1545 - Salários", "Salários", "1000.00");
        LinhaPo fundos = new LinhaPo(p, 3, 1, TipoLinhaPo.GRUPO, "1.9", null, "Fundos", null, "Fundos do Condomínio",
                BigDecimal.ZERO.setScale(2), new BigDecimal("50.00"), null, null);
        LinhaPo reserva = linha(p, 4, TipoLinhaPo.LINHA, "1.9.1", null, "Fundo de Reserva", "30.00");
        LinhaPo obras = linha(p, 5, TipoLinhaPo.LINHA, "1.9.2", null, "Fundo de Obras", "20.00");
        return new Po(p, List.of(grupo, l, fundos, reserva, obras), List.of(), Map.of(reserva.getId(), RESERVA));
    }

    private static PrevisaoOrcamentaria confirmada() {
        PrevisaoOrcamentaria p = new PrevisaoOrcamentaria(CONDOMINIO, UUID.randomUUID(), "a".repeat(64));
        p.registrarLeitura("po-protest", "PO", null, null, null, EstadoPrevisao.LIDA, null, null, BigDecimal.ONE,
                new BigDecimal("0.01"), Instant.EPOCH);
        p.confirmar(1, YearMonth.of(2026, 5), YearMonth.of(2027, 4), null, true, null, false, null, "admin",
                Instant.EPOCH);
        return p;
    }

    private static LinhaPo linha(PrevisaoOrcamentaria p, int ordem, TipoLinhaPo tipo, String codigo, String conta,
            String descricao, String orcado) {
        return new LinhaPo(p, ordem, 1, tipo, codigo, conta, null, null, descricao, BigDecimal.ZERO.setScale(2),
                new BigDecimal(orcado), null, null);
    }

    private void debito(Po po, UUID arquivo, String conta, String valor, LocalDate data) {
        lancamentos.add(lancamento(arquivo, ORDINARIO, conta, "0.00", valor, false, false, data));
    }

    private Lancamento lancamento(UUID arquivo, UUID fundo, String conta, String credito, String debito,
            boolean transferencia, boolean cota, LocalDate data) {
        var lido = new LancamentoLido(1, lancamentos.size() + 1, data, conta, conta == null ? "" : "CONTA " + conta, "",
                "Teste", new BigDecimal(credito), new BigDecimal(debito), BigDecimal.ZERO.setScale(2),
                new Enriquecimento(null, null, null, transferencia, cota));
        return new Lancamento(CONDOMINIO, arquivo, fundo, lido);
    }

    private static Fluxo fluxo(UUID arquivo, YearMonth mes) {
        return new Fluxo(arquivo, "fluxo-" + mes + ".pdf", "f".repeat(64), mes.atDay(1), mes.atEndOfMonth(),
                Instant.EPOCH, "gestor");
    }

    private PrevistoRealizado calcular(Po po, Periodo periodo, List<Fluxo> fluxos) {
        return CalculoPrevistoRealizado.calcular(entrada(po, periodo, fluxos, ORDINARIO)).resultado();
    }

    private Entrada entrada(Po po, Periodo periodo, List<Fluxo> fluxos, UUID ordinario) {
        if (po == null) {
            return new Entrada(null, null, null, null, null, null, ordinario, fluxos, lancamentos, null, null, null,
                    periodo);
        }
        return new Entrada(po.previsao(), "po.pdf", po.linhas(), po.deparas(), po.fundoPorLinha(),
                Map.of(ORDINARIO, "CONDOMÍNIO", RESERVA, "FUNDO DE RESERVA"), ordinario, fluxos, lancamentos, List.of(),
                new BigDecimal("20.0000"), List.of(), periodo);
    }
}
