package br.com.condominioauditoria.api.orcamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import br.com.condominioauditoria.api.orcamento.LigacaoFundosPo.PedidoFundos;
import br.com.condominioauditoria.api.orcamento.PedidoConfirmacao.LigacaoFundo;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.Evidencia;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.FundoResultado;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.GrupoResultado;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.SituacaoFundo;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/**
 * Lacunas do frontend sobre RFs aprovados, com setembro/2026 do piloto (golden privado): filtro de fundo
 * (RF-03.1.13), evidência de grupo e de total (RF-03.1.12) e alteração da ligação dos fundos depois da confirmação,
 * com trilha (RF-03.1.9). Pulado sem data/golden/privado.
 */
class FundosEEvidenciaGoldenTest {

    private static final String SETEMBRO = "2026-09";

    @Test
    void evidenciaDeGrupoEDeTotalSomaOsNumerosDaTela() {
        GoldenSetembro g = golden();
        g.confirmarMapa();
        PrevistoRealizado r = consultar(g, null);

        GrupoResultado contratos = r.grupos().stream().filter(x -> x.codigo().equals("1.3")).findFirst().orElseThrow();
        List<Evidencia> doGrupo = evidencia(g, CalculoPrevistoRealizado.alvoGrupo(contratos.linhaId()));
        List<Evidencia> total = evidencia(g, CalculoPrevistoRealizado.ALVO_TOTAL);

        assertThat(soma(doGrupo)).isEqualByComparingTo(contratos.realizado()).isEqualByComparingTo("341277.13");
        assertThat(doGrupo).hasSize(contratos.linhas().stream().mapToInt(PrevistoRealizado.LinhaResultado::lancamentos)
                .sum());
        assertThat(soma(total)).isEqualByComparingTo(r.totais().despesaRealizada()).isEqualByComparingTo("446176.89");
        assertThat(total).allSatisfy(ev -> {
            assertThat(ev.pagina()).isPositive();
            assertThat(ev.sha256()).hasSize(64);
        });
        assertThat(evidencia(g, "grupo:" + UUID.randomUUID())).isEmpty();
    }

    @Test
    void filtroDeFundo() {
        GoldenSetembro g = golden();
        g.confirmarMapa();
        CenarioPo c = g.cenario;

        PrevistoRealizado condominio = consultar(g, c.ordinario.getId());
        PrevistoRealizado reserva = consultar(g, c.reserva.getId());

        assertThat(condominio.totais().despesaRealizada()).isEqualByComparingTo("446176.89");
        assertThat(condominio.grupos()).isNotEmpty();
        assertThat(condominio.fundos()).isEmpty();
        assertThat(condominio.provisorio()).isTrue();
        assertThat(reserva.totais()).isNull();
        assertThat(reserva.grupos()).isEmpty();
        assertThat(reserva.provisorio()).isFalse();
        assertThat(reserva.fundos()).singleElement().satisfies(f -> {
            assertThat(f.fundo()).isEqualTo("FUNDO DE RESERVA");
            assertThat(f.arrecadado()).isEqualByComparingTo("14260.79");
        });
        assertThat(reserva.avisos()).extracting(PrevistoRealizado.Aviso::codigo).doesNotContain("A_REALOCAR");
        assertThatThrownBy(() -> consultar(g, UUID.randomUUID())).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Fundo não encontrado");
    }

    @Test
    void exportacaoComFiltroDeFundo() throws Exception {
        GoldenSetembro g = golden();
        g.confirmarMapa();
        var calculo = g.cenario.previstoRealizado.calcular(g.cenario.condominioId, SETEMBRO, null,
                g.cenario.reserva.getId());
        var rel = RelatorioPrevistoRealizado.montar("Condomínio Piloto", "FUNDO DE RESERVA", calculo, "admin",
                Instant.parse("2026-10-04T15:30:00Z"));

        String pdf = ExportacaoPrevistoRealizadoGoldenTest.textoDoPdf(new RelatorioPdf().gerar(rel));
        List<String> excel = ExportacaoPrevistoRealizadoGoldenTest.textosDoExcel(new RelatorioExcel().gerar(rel));

        assertThat(pdf).contains("Fundo FUNDO DE RESERVA", "13.548,60 14.260,79 712,19 105,3%")
                .doesNotContain("446.176,89").doesNotContain("PROVISÓRIO");
        assertThat(excel).contains("FUNDO DE RESERVA").doesNotContain("PROVISÓRIO", "Totais do fundo Condomínio");
    }

    @Test
    void adminCorrigeALigacaoDosFundosComTrilha() {
        GoldenSetembro g = golden();
        g.confirmarMapa();
        CenarioPo c = g.cenario;
        UUID l191 = g.linha("1.9.1").getId();
        UUID l192 = g.linha("1.9.2").getId();

        // RF-03.1.9: 1.9.2 ligada ao fundo "OBRAS" por engano
        c.publicados.clear();
        c.ligacaoFundos.alterar(c.condominioId, g.po.getId(), new PedidoFundos(List.of(
                new LigacaoFundo(l191, c.reserva.getId()), new LigacaoFundo(l192, c.obras.getId()))), "admin");
        PrevistoRealizado errado = consultar(g, null);

        FundoResultado obras = fundo(errado, "OBRAS");
        assertThat(obras.situacao()).isEqualTo(SituacaoFundo.COMPARADO);
        assertThat(List.of(obras.arrecadado(), obras.diferenca(), obras.execucao()))
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("25.13"), new BigDecimal("-9007.27"), new BigDecimal("0.3"));
        assertThat(fundo(errado, "OBRAS / REFORMAS / INFRA").situacao()).isEqualTo(SituacaoFundo.SEM_PREVISTO_NA_PO);
        assertThat(c.publicados).filteredOn(MudancaOrcamento.class::isInstance).hasSize(1);

        // O Admin corrige: os números voltam, e a trilha registra o anterior e o novo, com quem
        c.ligacaoFundos.alterar(c.condominioId, g.po.getId(), new PedidoFundos(List.of(
                new LigacaoFundo(l191, c.reserva.getId()), new LigacaoFundo(l192, c.obrasInfra.getId()))), "admin");
        PrevistoRealizado certo = consultar(g, null);

        assertThat(fundo(certo, "OBRAS / REFORMAS / INFRA").arrecadado()).isEqualByComparingTo("9705.06");
        assertThat(fundo(certo, "OBRAS").situacao()).isEqualTo(SituacaoFundo.SEM_PREVISTO_NA_PO);
        var alteracoes = c.eventos.stream().filter(e -> e.getTipo().equals(EventoPrevisao.FUNDOS_ALTERADOS)).toList();
        assertThat(alteracoes).hasSize(2).allMatch(e -> e.getUsuario().equals("admin"));
        assertThat(alteracoes.get(0).getDetalhe()).contains("1.9.2", "OBRAS / REFORMAS / INFRA → OBRAS");
        assertThat(alteracoes.get(1).getDetalhe()).contains("1.9.2", "OBRAS → OBRAS / REFORMAS / INFRA")
                .doesNotContain("1.9.1");

        // Sem mudança: nenhum evento novo
        c.ligacaoFundos.alterar(c.condominioId, g.po.getId(), new PedidoFundos(List.of(
                new LigacaoFundo(l191, c.reserva.getId()), new LigacaoFundo(l192, c.obrasInfra.getId()))), "admin");
        assertThat(c.eventos).filteredOn(e -> e.getTipo().equals(EventoPrevisao.FUNDOS_ALTERADOS)).hasSize(2);

        // Desligar uma linha: "linha 1.9.2 sem fundo ligado"
        c.ligacaoFundos.alterar(c.condominioId, g.po.getId(), new PedidoFundos(List.of(
                new LigacaoFundo(l191, c.reserva.getId()))), "admin");
        assertThat(consultar(g, null).fundos()).anyMatch(f -> "1.9.2".equals(f.linhaCodigo())
                && f.situacao() == SituacaoFundo.LINHA_SEM_FUNDO);
    }

    @Test
    void ligacaoInvalidaEhRecusadaSemMudarNada() {
        GoldenSetembro g = golden();
        CenarioPo c = g.cenario;
        UUID l191 = g.linha("1.9.1").getId();
        UUID l192 = g.linha("1.9.2").getId();
        int antes = c.poFundos.size();

        assertThatThrownBy(() -> c.ligacaoFundos.alterar(c.condominioId, g.po.getId(), new PedidoFundos(List.of(
                new LigacaoFundo(l191, c.obras.getId()), new LigacaoFundo(l192, c.obras.getId()))), "admin"))
                .isInstanceOf(ConfirmacaoRecusadaException.class);
        assertThatThrownBy(() -> c.ligacaoFundos.alterar(c.condominioId, g.po.getId(), new PedidoFundos(List.of(
                new LigacaoFundo(l191, c.reserva.getId()), new LigacaoFundo(l191, c.obras.getId()))), "admin"))
                .isInstanceOf(ConfirmacaoRecusadaException.class);
        assertThatThrownBy(() -> c.ligacaoFundos.alterar(c.condominioId, g.po.getId(), new PedidoFundos(List.of(
                new LigacaoFundo(l191, c.ordinario.getId()))), "admin"))
                .isInstanceOf(ConfirmacaoRecusadaException.class);
        assertThatThrownBy(() -> c.ligacaoFundos.alterar(c.condominioId, g.po.getId(), new PedidoFundos(List.of(
                new LigacaoFundo(g.linha("1.3.10").getId(), c.obras.getId()))), "admin"))
                .isInstanceOf(ConfirmacaoRecusadaException.class);
        assertThat(c.poFundos).hasSize(antes);
        assertThat(c.eventos).noneMatch(e -> e.getTipo().equals(EventoPrevisao.FUNDOS_ALTERADOS));
    }

    private static PrevistoRealizado consultar(GoldenSetembro g, UUID fundo) {
        return g.cenario.previstoRealizado.consultar(g.cenario.condominioId, SETEMBRO, null, fundo);
    }

    private static List<Evidencia> evidencia(GoldenSetembro g, String alvo) {
        return g.cenario.previstoRealizado.evidencia(g.cenario.condominioId, SETEMBRO, null, alvo);
    }

    private static BigDecimal soma(List<Evidencia> lista) {
        return lista.stream().map(Evidencia::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static FundoResultado fundo(PrevistoRealizado r, String nome) {
        return r.fundos().stream().filter(f -> nome.equals(f.fundo())).findFirst().orElseThrow();
    }

    private static GoldenSetembro golden() {
        Optional<GoldenSetembro> g = GoldenSetembro.carregar();
        assumeTrue(g.isPresent() && GoldenSetembro.mapa().isPresent(), "golden privado ausente");
        return g.get();
    }
}
