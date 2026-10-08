package br.com.condominioauditoria.api.orcamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.condominioauditoria.api.arquivo.Arquivo;
import br.com.condominioauditoria.api.contabil.Lancamento;
import br.com.condominioauditoria.api.mensagens.ResultadoProcessamento.Enriquecimento;
import br.com.condominioauditoria.api.mensagens.ResultadoProcessamento.LancamentoLido;
import br.com.condominioauditoria.api.orcamento.PrevisaoDtos.PedidoProrrogacao;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.Aviso;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.MesExercicio;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * RF-11.3 e ADR 0005, Decisão 4: PO do mês com prorrogação. Exercício de teste 04/2025 a 03/2026 (a PO do piloto
 * confirmada com outras datas) e a PO 2026/2027 do piloto (05/2026 a 04/2027): 04/2026 fica entre os dois.
 */
class ProrrogacaoPoTest {

    private static final YearMonth ABRIL = YearMonth.of(2026, 4);

    private final CenarioPo cenario = new CenarioPo();

    @Test
    void mesEntreDoisExerciciosFicaSemPoAprovada() {
        anterior();
        cenario.poConfirmada();

        assertThat(cenario.consultaVigente(ABRIL)).isEmpty();
        PrevistoRealizado r = cenario.previstoRealizado.consultar(cenario.condominioId, "2026-04", null);
        assertThat(r.situacao()).isEqualTo(PrevistoRealizado.Situacao.SEM_PO);
        assertThat(r.mensagem()).isEqualTo("Sem PO aprovada para 04/2026");
    }

    @Test
    void prorrogadaAteAbrilUsaAPoAnteriorComAvisoEVaiParaATrilha() {
        PrevisaoOrcamentaria anterior = anterior();
        cenario.poConfirmada();

        var detalhe = cenario.prorrogacao.prorrogar(cenario.condominioId, anterior.getId(),
                new PedidoProrrogacao("2026-04", "PO 2026/2027 aprovada só na AGO de maio"), "admin");

        assertThat(detalhe.previsao().prorrogacao().de()).isEqualTo("2026-04");
        assertThat(detalhe.previsao().prorrogacao().ate()).isEqualTo("2026-04");
        assertThat(cenario.consultaVigente(ABRIL)).contains(anterior);
        PrevistoRealizado r = cenario.previstoRealizado.consultar(cenario.condominioId, "2026-04", null);
        assertThat(r.po().id()).isEqualTo(anterior.getId());
        assertThat(r.situacao()).isEqualTo(PrevistoRealizado.Situacao.SEM_FLUXO);
        assertThat(r.meses()).singleElement().extracting(MesExercicio::prorrogado).isEqualTo(true);
        assertThat(r.avisos()).extracting(Aviso::codigo).contains("PO_PRORROGADA");
        assertThat(r.avisos().stream().filter(a -> a.codigo().equals("PO_PRORROGADA")).findFirst().orElseThrow().texto())
                .isEqualTo("PO prorrogada: 04/2026 usa a PO do exercício 04/2025 a 03/2026, prorrogada até 04/2026 por"
                        + " admin. Justificativa: PO 2026/2027 aprovada só na AGO de maio");
        EventoPrevisao e = cenario.eventos.getLast();
        assertThat(e.getTipo()).isEqualTo(EventoPrevisao.PRORROGADA);
        assertThat(e.getUsuario()).isEqualTo("admin");
        assertThat(e.getJustificativa()).isEqualTo("PO 2026/2027 aprovada só na AGO de maio");
        assertThat(cenario.publicados).isNotEmpty();
    }

    @Test
    void semJustificativaOuSobreMesComPoConfirmadaEhRecusada() {
        PrevisaoOrcamentaria anterior = anterior();
        cenario.poConfirmada();

        assertThatThrownBy(() -> cenario.prorrogacao.prorrogar(cenario.condominioId, anterior.getId(),
                new PedidoProrrogacao("2026-04", "  "), "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));
        assertThatThrownBy(() -> cenario.prorrogacao.prorrogar(cenario.condominioId, anterior.getId(),
                new PedidoProrrogacao("2026-05", "atraso"), "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(e.getReason()).startsWith("05/2026 já tem PO confirmada");
                });
        assertThatThrownBy(() -> cenario.prorrogacao.prorrogar(cenario.condominioId, anterior.getId(),
                new PedidoProrrogacao("2026-03", "atraso"), "admin"))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(anterior.getProrrogadaAte()).isNull();
    }

    @Test
    void poNovaConfirmadaEncurtaAProrrogacao() {
        PrevisaoOrcamentaria anterior = anterior();
        cenario.prorrogacao.prorrogar(cenario.condominioId, anterior.getId(),
                new PedidoProrrogacao("2026-05", "assembleia adiada"), "admin");

        cenario.poConfirmada();

        assertThat(anterior.getProrrogadaAte()).isEqualTo(ABRIL);
        EventoPrevisao e = cenario.eventos.stream()
                .filter(x -> x.getTipo().equals(EventoPrevisao.PRORROGACAO_ENCURTADA)).findFirst().orElseThrow();
        assertThat(e.getPrevisaoId()).isEqualTo(anterior.getId());
        assertThat(e.getJustificativa()).startsWith("PO 2026/2027 confirmada");
        assertThat(cenario.consultaVigente(YearMonth.of(2026, 5)).orElseThrow().getId())
                .isNotEqualTo(anterior.getId());
    }

    @Test
    void poNovaQueComecaLogoAposOExercicioDesfazAProrrogacao() {
        PrevisaoOrcamentaria anterior = anterior();
        cenario.prorrogacao.prorrogar(cenario.condominioId, anterior.getId(),
                new PedidoProrrogacao("2026-05", "assembleia adiada"), "admin");
        PrevisaoOrcamentaria nova = cenario.lerPo(PoDoPiloto.padrao());
        var p = cenario.pedidoDoPiloto(nova);

        cenario.confirmacao.confirmar(cenario.condominioId, nova.getId(), new PedidoConfirmacao("2026-04", "2027-03",
                p.ataArquivoId(), false, LocalDate.of(2026, 3, 30), p.codigosEfetivos(), p.fundos(), false, false, null),
                "admin");

        assertThat(anterior.getProrrogadaAte()).isNull();
        assertThat(cenario.consultaVigente(ABRIL)).contains(nova);
    }

    @Test
    void mesesProrrogadosFicamForaDoAcumulado() {
        PrevisaoOrcamentaria anterior = anterior();
        cenario.prorrogacao.prorrogar(cenario.condominioId, anterior.getId(),
                new PedidoProrrogacao("2026-04", "assembleia adiada"), "admin");
        Arquivo marco = cenario.fluxo("fluxo-2026-03.pdf", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), 1);
        Arquivo abril = cenario.fluxo("fluxo-2026-04.pdf", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), 1);
        debito(marco, "100.00", LocalDate.of(2026, 3, 10));
        debito(abril, "250.00", LocalDate.of(2026, 4, 10));

        PrevistoRealizado r = cenario.previstoRealizado.consultar(cenario.condominioId, "acumulado", anterior.getId());

        assertThat(r.meses()).hasSize(13);
        assertThat(r.meses().subList(0, 12)).noneMatch(MesExercicio::prorrogado);
        MesExercicio ultimo = r.meses().getLast();
        assertThat(ultimo.mes()).isEqualTo("2026-04");
        assertThat(ultimo.prorrogado()).isTrue();
        assertThat(ultimo.despesaRealizada()).isEqualByComparingTo("250.00");
        assertThat(r.mesesSomados()).containsExactly("2026-03");
        assertThat(r.totais().despesaRealizada()).isEqualByComparingTo("100.00");
        assertThat(r.avisos()).extracting(Aviso::texto).contains("PO prorrogada até 04/2026: abr/2026 aparece depois do"
                + " exercício, marcado \"prorrogado\", e não entra no acumulado.");

        PrevistoRealizado mes = cenario.previstoRealizado.consultar(cenario.condominioId, "2026-04", null);
        assertThat(mes.totais().despesaRealizada()).isEqualByComparingTo("250.00");
    }

    @Test
    void desfazerVoltaASemPoEVaiParaATrilha() {
        PrevisaoOrcamentaria anterior = anterior();
        cenario.prorrogacao.prorrogar(cenario.condominioId, anterior.getId(),
                new PedidoProrrogacao("2026-04", "assembleia adiada"), "admin");

        cenario.prorrogacao.desfazer(cenario.condominioId, anterior.getId(), "admin");

        assertThat(cenario.consultaVigente(ABRIL)).isEmpty();
        assertThat(cenario.eventos.getLast().getTipo()).isEqualTo(EventoPrevisao.PRORROGACAO_DESFEITA);
        assertThatThrownBy(() -> cenario.prorrogacao.desfazer(cenario.condominioId, anterior.getId(), "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    /** PO do exercício de teste 04/2025 a 03/2026 (a do piloto, confirmada com essas datas). */
    private PrevisaoOrcamentaria anterior() {
        PrevisaoOrcamentaria po = cenario.lerPo(PoDoPiloto.padrao());
        var p = cenario.pedidoDoPiloto(po);
        cenario.confirmacao.confirmar(cenario.condominioId, po.getId(), new PedidoConfirmacao("2025-04", "2026-03",
                p.ataArquivoId(), false, LocalDate.of(2025, 3, 30), p.codigosEfetivos(), p.fundos(), false, false, null),
                "admin");
        return po;
    }

    private void debito(Arquivo fluxo, String valor, LocalDate data) {
        var lido = new LancamentoLido(1, cenario.lancamentos.size() + 1, data, "9999", "Teste", "", "Teste",
                BigDecimal.ZERO.setScale(2), new BigDecimal(valor), BigDecimal.ZERO.setScale(2),
                new Enriquecimento(null, null, null, false, false));
        cenario.lancamentos.add(new Lancamento(cenario.condominioId, fluxo.getId(), cenario.ordinario.getId(), lido));
    }
}
