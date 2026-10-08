package br.com.condominioauditoria.backend.orcamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.condominioauditoria.backend.orcamento.PrevistoRealizado.SituacaoMes;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** RF-11.10 e RF-11.11 sem o golden: meses sem fluxo, padrão do exercício e comparação com a coluna impressa. */
class IndicadoresTest {

    private final CenarioPo cenario = new CenarioPo();

    @Test
    void semFluxoNenhumPontoEmZero() {
        PrevisaoOrcamentaria po = cenario.poConfirmada();

        Indicadores.Resultado r = cenario.indicadores.indicadores(cenario.condominioId, null, null);

        assertThat(r.poId()).isEqualTo(po.getId());
        assertThat(r.inicio()).isEqualTo("2026-05");
        assertThat(r.execucaoMensal()).hasSize(12).allMatch(p -> p.situacao() == SituacaoMes.SEM_FLUXO
                && p.execucao() == null && p.previsto() == null && p.alvo() == null);
        assertThat(r.regra20()).allMatch(p -> p.percentual() == null);
        assertThat(r.acumulado()).allMatch(p -> p.realizadoAcumulado() == null);
        assertThat(r.maioresDiferencas().acima()).isEmpty();
        assertThat(r.comparacao()).isNull();
        assertThat(r.dadosDe()).isNull();
        assertThat(r.avisos()).containsExactly(
                "12 meses do exercício sem números (sem fluxo carregado ou com dois fluxos)",
                "Comparação entre exercícios: sem exercício anterior a este");
    }

    @Test
    void mesComFluxoEComparacaoComAColunaImpressa() {
        PrevisaoOrcamentaria po = cenario.lerPo(PoDoPiloto.padrao().comColunaAnterior());
        cenario.confirmacao.confirmar(cenario.condominioId, po.getId(), cenario.pedidoDoPiloto(po), "admin");
        cenario.fluxo("fluxo-2026-09.pdf", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 0);

        Indicadores.Resultado r = cenario.indicadores.indicadores(cenario.condominioId, po.getId(), null);

        assertThat(r.execucaoMensal().get(4).situacao()).isEqualTo(SituacaoMes.COM_FLUXO);
        assertThat(r.execucaoMensal().get(4).previsto()).isEqualByComparingTo("451620.13");
        assertThat(r.execucaoMensal().get(4).realizado()).isEqualByComparingTo("0.00");
        assertThat(r.acumulado().get(4).previstoAcumulado()).isEqualByComparingTo("451620.13");
        assertThat(r.dadosDe()).isNotNull();
        assertThat(r.comparacao().exercicios()).extracting(Indicadores.ExercicioDaComparacao::rotulo)
                .containsExactly("2026/2027", "2025/2026 (coluna impressa)");
        assertThat(r.comparacao().grupos()).filteredOn(gr -> gr.codigo().equals("1.3")).singleElement()
                .satisfies(gr -> assertThat(gr.previstoMes()).extracting(java.math.BigDecimal::toPlainString)
                        .containsExactly("336274.18", "348631.55"));
        // RF-11.12: o clique abre a evidência do grupo; a coluna impressa não tem realizado, logo nem PO nem alvo
        assertThat(r.comparacao().exercicios()).extracting(Indicadores.ExercicioDaComparacao::poId)
                .containsExactly(po.getId(), null);
        assertThat(r.comparacao().grupos()).allSatisfy(gr -> assertThat(gr.alvos()).hasSize(2).last().isNull());
        assertThat(r.comparacao().grupos()).filteredOn(gr -> gr.codigo().equals("1.3")).singleElement()
                .satisfies(gr -> assertThat(gr.alvos().getFirst()).startsWith("grupo:"));
        assertThat(r.avisos()).containsExactly(
                "11 meses do exercício sem números (sem fluxo carregado ou com dois fluxos)");
    }

    @Test
    void semPoConfirmadaOuPoDeOutroCondominio() {
        assertThatThrownBy(() -> cenario.indicadores.indicadores(cenario.condominioId, null, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        cenario.poConfirmada();
        assertThatThrownBy(() -> cenario.indicadores.indicadores(cenario.condominioId, UUID.randomUUID(), null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
