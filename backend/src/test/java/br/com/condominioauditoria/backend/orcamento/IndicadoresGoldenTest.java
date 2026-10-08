package br.com.condominioauditoria.backend.orcamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import br.com.condominioauditoria.backend.orcamento.Indicadores.Diferenca;
import br.com.condominioauditoria.backend.orcamento.Indicadores.PontoExecucao;
import br.com.condominioauditoria.backend.orcamento.Indicadores.PontoFundo;
import br.com.condominioauditoria.backend.orcamento.Indicadores.PontoRegra20;
import br.com.condominioauditoria.backend.orcamento.Indicadores.SerieFundo;
import br.com.condominioauditoria.backend.orcamento.PrevistoRealizado.SituacaoMes;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * RF-11.10 e RF-11.11 com setembro/2026 do piloto (golden privado): os valores dos gráficos são os da tela de
 * previsto × realizado, centavo a centavo. Pulado sem data/golden/privado.
 */
class IndicadoresGoldenTest {

    private static final int SETEMBRO = 4;

    @Test
    void setembroNosGraficosIgualAoPrevistoRealizado() {
        GoldenSetembro g = golden();
        g.confirmarMapa();
        CenarioPo c = g.cenario;

        Indicadores.Resultado r = c.indicadores.indicadores(c.condominioId, g.po.getId(), null);
        PrevistoRealizado tela = c.previstoRealizado.consultar(c.condominioId, "2026-09", g.po.getId());

        assertThat(r.rotulo()).isEqualTo("2026/2027");
        assertThat(r.dadosDe()).isNotNull();
        // Gráfico 1: 98,8% em setembro; os outros 11 meses sem número (nunca zero)
        assertThat(r.execucaoMensal()).hasSize(12);
        PontoExecucao set = r.execucaoMensal().get(SETEMBRO);
        assertThat(set.mes()).isEqualTo("2026-09");
        assertThat(set.execucao()).isEqualByComparingTo("98.8").isEqualByComparingTo(tela.totais().execucao());
        assertThat(set.realizado()).isEqualByComparingTo("446176.89");
        assertThat(set.previsto()).isEqualByComparingTo("451620.13");
        assertThat(r.execucaoMensal()).filteredOn(p -> !p.mes().equals("2026-09"))
                .allMatch(p -> p.situacao() == SituacaoMes.SEM_FLUXO && p.execucao() == null && p.realizado() == null);
        // Gráfico 2: 8,6%, limite 20%, cenário máximo 8,8% provisório
        PontoRegra20 regra = r.regra20().get(SETEMBRO);
        assertThat(regra.percentual()).isEqualByComparingTo("8.6");
        assertThat(regra.excesso()).isEqualByComparingTo("38880.19");
        assertThat(regra.limitePercentual()).isEqualByComparingTo("20");
        assertThat(regra.percentualCenarioMaximo()).isEqualByComparingTo("8.8");
        assertThat(regra.provisorio()).isTrue();
        assertThat(regra.acimaDoLimite()).isFalse();
        // Gráfico 3: o acumulado de setembro é o próprio setembro
        assertThat(r.acumulado().get(SETEMBRO).realizadoAcumulado()).isEqualByComparingTo("446176.89");
        assertThat(r.acumulado().get(SETEMBRO - 1).realizadoAcumulado()).isNull();
        // Gráfico 4: Contratos em setembro
        assertThat(r.realizadoPorGrupo()).filteredOn(s -> s.codigo().equals("1.3")).singleElement()
                .satisfies(s -> assertThat(s.pontos().get(SETEMBRO).valor()).isEqualByComparingTo("341277.13"));
        assertThat(r.realizadoPorGrupo()).noneMatch(s -> s.codigo().equals("1.9"));
        // Gráfico 5: 1.3.10 entre as mais acima, com +6.793,38 e a evidência da linha
        Diferenca vigia = r.maioresDiferencas().acima().stream().filter(d -> d.codigo().equals("1.3.10"))
                .findFirst().orElseThrow();
        assertThat(vigia.diferenca()).isEqualByComparingTo("6793.38");
        assertThat(vigia.alvo()).isEqualTo("linha:" + vigia.linhaId());
        assertThat(c.previstoRealizado.evidencia(c.condominioId, "2026-09", g.po.getId(), vigia.alvo()).stream()
                .map(PrevistoRealizado.Evidencia::valor).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add))
                .isEqualByComparingTo("86816.34");
        assertThat(r.maioresDiferencas().acima()).hasSizeLessThanOrEqualTo(10);
        assertThat(r.maioresDiferencas().abaixo()).hasSizeLessThanOrEqualTo(10);
        // Gráfico 6: Reserva 14.260,79 × 13.548,60 e Obras 9.705,06 × 9.032,40
        assertThat(pontoDe(r, "FUNDO DE RESERVA").arrecadado()).isEqualByComparingTo("14260.79");
        assertThat(pontoDe(r, "FUNDO DE RESERVA").previsto()).isEqualByComparingTo("13548.60");
        assertThat(pontoDe(r, "OBRAS / REFORMAS / INFRA").arrecadado()).isEqualByComparingTo("9705.06");
        assertThat(pontoDe(r, "OBRAS / REFORMAS / INFRA").previsto()).isEqualByComparingTo("9032.40");
        // Gráfico 7: este exercício e a coluna "Orçado anterior" da própria PO
        assertThat(r.comparacao()).isNotNull();
        assertThat(r.comparacao().exercicios()).hasSize(2);
        assertThat(r.comparacao().exercicios().getFirst().execucao()).isEqualByComparingTo("98.8");
        assertThat(r.comparacao().exercicios().get(1).rotulo()).endsWith("(coluna impressa)");
    }

    @Test
    void filtroDoFundoDeReservaMostraSoOGraficoDele() {
        GoldenSetembro g = golden();
        g.confirmarMapa();
        CenarioPo c = g.cenario;

        Indicadores.Resultado reserva = c.indicadores.indicadores(c.condominioId, null, c.reserva.getId());
        Indicadores.Resultado condominio = c.indicadores.indicadores(c.condominioId, null, c.ordinario.getId());

        assertThat(reserva.execucaoMensal()).isNull();
        assertThat(reserva.fundos()).extracting(SerieFundo::fundo).containsExactly("FUNDO DE RESERVA");
        assertThat(condominio.fundos()).isNull();
        assertThat(condominio.execucaoMensal().get(SETEMBRO).execucao()).isEqualByComparingTo("98.8");
    }

    private static PontoFundo pontoDe(Indicadores.Resultado r, String fundo) {
        return r.fundos().stream().filter(f -> f.fundo().equals(fundo)).findFirst().orElseThrow().pontos()
                .get(SETEMBRO);
    }

    private static GoldenSetembro golden() {
        Optional<GoldenSetembro> g = GoldenSetembro.carregar();
        assumeTrue(g.isPresent() && GoldenSetembro.mapa().isPresent(), "golden privado ausente");
        return g.get();
    }
}
