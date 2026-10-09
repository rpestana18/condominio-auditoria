package br.com.condominioauditoria.api.orcamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.condominioauditoria.api.dto.request.budget.BudgetConfirmationRequest;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.orcamento.DeparaDtos.AcaoLote;
import br.com.condominioauditoria.api.orcamento.DeparaDtos.FiltroDepara;
import br.com.condominioauditoria.api.orcamento.DeparaDtos.PedidoDestino;
import br.com.condominioauditoria.api.orcamento.DeparaDtos.PedidoLote;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/** ADR 0004, passo 6: de-para por versão da PO, sugestões que nunca se confirmam sozinhas e trilha de toda mudança. */
class ServicoDeparaTest {

    private final CenarioPo cenario = new CenarioPo();
    private Budget po;

    @BeforeEach
    void preparar() {
        po = cenario.poConfirmada();
        LocalDate set = LocalDate.of(2026, 9, 10);
        cenario.debito("1621", "MATERIAL HIDRÁULICO", "4949.99", set);
        cenario.debito("1108", "PRO LABORE", "7120.00", set);
        cenario.debito("1324", "ESTORNOS", "3987.12", set);
        // Fora do exercício (04/2026): não entra na lista
        cenario.debito("9999", "CONTA ANTIGA", "10.00", LocalDate.of(2026, 4, 30));
    }

    @Test
    void sugestoesPeloNomeFicamSugeridasComMotivoETrilha() {
        var r = cenario.depara.sugerir(cenario.condominioId, po.getId(), "admin");

        assertThat(r.peloNome()).isEqualTo(1);
        assertThat(r.semSugestao()).extracting(DeparaDtos.ContaSemSugestao::conta).containsExactly("1108", "1324");
        DeparaConta d = unico("1621");
        assertThat(d.getEstado()).isEqualTo(EstadoDepara.SUGERIDO);
        assertThat(d.getOrigem()).isEqualTo(OrigemDepara.NOME);
        assertThat(d.getLinhaPoId()).isEqualTo(cenario.linha(po, "1.7.8", 0).getId());
        assertThat(d.getLinhaPoId()).isNotEqualTo(cenario.linha(po, "1.3.23", 0).getId());
        assertThat(d.getMotivo()).contains("MATERIAL HIDRAULICO");
        assertThat(cenario.eventosDepara).singleElement().satisfies(e -> {
            assertThat(e.getAcao()).isEqualTo(EventoDepara.Acao.SUGERIDO);
            assertThat(e.getDestinoAnterior()).isNull();
            assertThat(e.getDestinoNovo()).startsWith("1.7.8");
            assertThat(e.getUsuario()).isEqualTo("admin");
        });

        var lista = cenario.depara.listar(cenario.condominioId, po.getId(), FiltroDepara.TODAS);
        assertThat(lista.resumo()).isEqualTo(new DeparaDtos.ResumoDepara(3, 0, 1, 0, 2));
        assertThat(lista.contas()).extracting(DeparaDtos.ContaDepara::conta).containsExactly("1108", "1324", "1621");
        assertThat(DeparaEfetivo.confirmados(cenario.deparas)).isEmpty();
    }

    @Test
    void sugerirDuasVezesNaoDuplica() {
        cenario.depara.sugerir(cenario.condominioId, po.getId(), "admin");
        var r = cenario.depara.sugerir(cenario.condominioId, po.getId(), "admin");

        assertThat(r.criadas()).isZero();
        assertThat(cenario.deparas).hasSize(1);
        assertThat(cenario.eventosDepara).hasSize(1);
    }

    @Test
    void planilhaEntraSugeridaEConfirmadaNaoMuda() {
        var r = cenario.depara.carregarPlanilha(cenario.condominioId, po.getId(), "mapa.csv", """
                1621;1.7.8
                1108;1.3.20
                1324;AJUSTE (estorno)
                7777;1.9.1
                """, "admin");

        assertThat(r.aceitas()).isEqualTo(3);
        assertThat(r.recusadas()).singleElement().satisfies(x -> assertThat(x.linha()).isEqualTo(4));
        assertThat(cenario.deparas).allMatch(d -> d.getEstado() == EstadoDepara.SUGERIDO
                && d.getOrigem() == OrigemDepara.PLANILHA);
        assertThat(DeparaEfetivo.confirmados(cenario.deparas)).isEmpty();

        cenario.depara.lote(cenario.condominioId, po.getId(), new PedidoLote(AcaoLote.CONFIRMAR, List.of("1621")), "admin");
        var segunda = cenario.depara.carregarPlanilha(cenario.condominioId, po.getId(), "mapa.csv", "1621;1.3.20\n",
                "admin");
        assertThat(segunda.aceitas()).isZero();
        assertThat(segunda.ignoradas()).singleElement().satisfies(i -> assertThat(i.motivo()).contains("já confirmada"));
        assertThat(unico("1621").getLinhaPoId()).isEqualTo(cenario.linha(po, "1.7.8", 0).getId());
    }

    @Test
    void loteConfirmaERecusaComUmEventoPorConta() {
        cenario.depara.carregarPlanilha(cenario.condominioId, po.getId(), null, "1621;1.7.8\n1108;1.3.20\n1324;AJUSTE\n",
                "admin");
        int antes = cenario.eventosDepara.size();

        var r = cenario.depara.lote(cenario.condominioId, po.getId(),
                new PedidoLote(AcaoLote.CONFIRMAR, List.of("1621", "1108", "1324", "5555")), "admin");

        assertThat(r.alteradas()).isEqualTo(3);
        assertThat(r.ignoradas()).extracting(DeparaDtos.ContaIgnorada::conta).containsExactly("5555");
        assertThat(cenario.eventosDepara.subList(antes, cenario.eventosDepara.size()))
                .hasSize(3).allMatch(e -> e.getAcao() == EventoDepara.Acao.CONFIRMADO
                        && e.getEstadoAnterior() == EstadoDepara.SUGERIDO && e.getEstadoNovo() == EstadoDepara.CONFIRMADO);
        assertThat(DeparaEfetivo.confirmados(cenario.deparas)).containsOnlyKeys("1108", "1324", "1621");

        cenario.depara.lote(cenario.condominioId, po.getId(), new PedidoLote(AcaoLote.RECUSAR, List.of("1324")), "admin");
        assertThat(DeparaEfetivo.confirmados(cenario.deparas)).containsOnlyKeys("1108", "1621");
        assertThat(cenario.depara.listar(cenario.condominioId, po.getId(), FiltroDepara.PENDENTES).contas())
                .extracting(DeparaDtos.ContaDepara::conta).containsExactly("1324");
    }

    @Test
    void trocaDeDestinoRegistraAnteriorENovo() {
        cenario.depara.definir(cenario.condominioId, po.getId(), "1108",
                new PedidoDestino(TipoDestino.LINHA_PO, cenario.linha(po, "1.3.20", 0).getId(), null, null), "admin");
        cenario.depara.definir(cenario.condominioId, po.getId(), "1108",
                new PedidoDestino(TipoDestino.LINHA_PO, cenario.linha(po, "1.3.1", 0).getId(), null, true), "admin2");

        EventoDepara troca = cenario.eventosDepara.getLast();
        assertThat(troca.getAcao()).isEqualTo(EventoDepara.Acao.ALTERADO);
        assertThat(troca.getDestinoAnterior()).startsWith("1.3.20");
        assertThat(troca.getDestinoNovo()).startsWith("1.3.1 ");
        assertThat(troca.getUsuario()).isEqualTo("admin2");
        assertThat(troca.getEstadoAnterior()).isEqualTo(EstadoDepara.CONFIRMADO);
        assertThat(cenario.depara.eventos(cenario.condominioId, po.getId())).hasSize(2);
    }

    @Test
    void destinoInvalidoERecusado() {
        assertThatThrownBy(() -> cenario.depara.definir(cenario.condominioId, po.getId(), "1108",
                new PedidoDestino(TipoDestino.LINHA_PO, cenario.linha(po, "1.9.1", 0).getId(), null, null), "admin"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("1.9");
        assertThatThrownBy(() -> cenario.depara.definir(cenario.condominioId, po.getId(), "1108",
                new PedidoDestino(TipoDestino.LINHA_PO, cenario.linha(po, "1.3", 0).getId(), null, null), "admin"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> cenario.depara.definir(cenario.condominioId, po.getId(), "abc",
                new PedidoDestino(TipoDestino.AJUSTE, null, null, null), "admin"))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(cenario.deparas).isEmpty();
    }

    @Test
    void poNaoConfirmadaNaoTemDepara() {
        Budget lida = cenario.lerPo(PoDoPiloto.padrao());

        assertThatThrownBy(() -> cenario.depara.sugerir(cenario.condominioId, lida.getId(), "admin"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Confirme a PO");
    }

    @Test
    void novaVersaoRecebeOAnteriorComoSugestaoSemHerdarConfirmacao() {
        cenario.depara.carregarPlanilha(cenario.condominioId, po.getId(), null, "1621;1.7.8\n1324;AJUSTE (estorno)\n",
                "admin");
        cenario.depara.lote(cenario.condominioId, po.getId(), new PedidoLote(AcaoLote.CONFIRMAR, List.of("1621", "1324")),
                "admin");
        Budget v2 = cenario.lerPo(PoDoPiloto.padrao());
        var pedido = cenario.pedidoDoPiloto(v2);
        cenario.confirmacao.confirm(cenario.condominioId, v2.getId(), new BudgetConfirmationRequest("2026-10", "2027-04",
                pedido.minutesFileId(), false, pedido.approvalDate(), pedido.effectiveCodes(), pedido.funds(), true,
                false, null), "admin");

        var r = cenario.depara.sugerir(cenario.condominioId, v2.getId(), "admin");

        assertThat(r.daVersaoAnterior()).isEqualTo(2);
        var iguais = cenario.depara.listar(cenario.condominioId, v2.getId(), FiltroDepara.IGUAIS_VERSAO_ANTERIOR);
        assertThat(iguais.contas()).extracting(DeparaDtos.ContaDepara::conta).containsExactly("1324", "1621");
        assertThat(iguais.contas()).allMatch(c -> c.estado() == EstadoDepara.SUGERIDO
                && c.origem() == OrigemDepara.VERSAO_ANTERIOR && c.motivo().startsWith("igual à versão anterior"));
        assertThat(iguais.contas().get(1).destino().linhaId()).isEqualTo(cenario.linha(v2, "1.7.8", 0).getId());
        // A versão 1 não muda; a versão 2 não herda confirmação
        assertThat(DeparaEfetivo.confirmados(cenario.deparas.stream()
                .filter(d -> d.getPrevisaoId().equals(po.getId())).toList())).containsOnlyKeys("1324", "1621");
        assertThat(DeparaEfetivo.confirmados(cenario.deparas.stream()
                .filter(d -> d.getPrevisaoId().equals(v2.getId())).toList())).isEmpty();

        cenario.depara.lote(cenario.condominioId, v2.getId(),
                new PedidoLote(AcaoLote.CONFIRMAR, iguais.contas().stream().map(DeparaDtos.ContaDepara::conta).toList()),
                "admin");
        assertThat(cenario.depara.listar(cenario.condominioId, v2.getId(), null).resumo().confirmadas()).isEqualTo(2);
    }

    private DeparaConta unico(String conta) {
        return cenario.deparas.stream().filter(d -> d.getContaCodigo().equals(conta)).findFirst().orElseThrow();
    }
}
