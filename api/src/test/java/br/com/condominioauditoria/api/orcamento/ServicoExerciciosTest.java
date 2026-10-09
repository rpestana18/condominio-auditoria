package br.com.condominioauditoria.api.orcamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.condominioauditoria.api.dto.request.budget.BudgetConfirmationRequest;
import br.com.condominioauditoria.api.dto.request.budget.BudgetExtensionRequest;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.orcamento.ColunaImpressa.GrupoColuna;
import br.com.condominioauditoria.api.orcamento.ColunaImpressa.LinhaColuna;
import br.com.condominioauditoria.api.orcamento.ExercicioDtos.ConferenciaColuna;
import br.com.condominioauditoria.api.orcamento.ExercicioDtos.Exercicio;
import br.com.condominioauditoria.api.orcamento.ExercicioDtos.MesDoExercicio;
import br.com.condominioauditoria.api.orcamento.ExercicioDtos.TipoExercicio;
import br.com.condominioauditoria.api.orcamento.PrevistoRealizado.SituacaoMes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * RF-11.4 e RF-11.5 (ADR 0005, Decisão 3): lista de exercícios e "PO anterior pela coluna impressa", com a coluna
 * "Orçado anterior" real da PO 2026/2027 do piloto (valores do PDF nos grupos e nas linhas citadas).
 */
class ServicoExerciciosTest {

    private static final String AVISO_1_6 = "Grupo 1.6 DESPESAS ADMINISTRATIVAS: subtotal impresso 18.525,42; soma das"
            + " linhas 18.746,25 (diferença 220,83). Vale a soma das linhas.";
    private static final String AVISO_TOTAL = "Nesta coluna o total impresso (441.304,38) não inclui os fundos: confere"
            + " com a soma dos subtotais sem os fundos (441.304,39).";

    private final CenarioPo cenario = new CenarioPo();

    @Test
    void semPoAnteriorAColunaImpressaViraOExercicio2025de2026SoComPrevisto() {
        Budget po = confirmar(PoDoPiloto.padrao().comColunaAnterior(), "2026-05", "2027-04");

        List<Exercicio> lista = cenario.exercicios.listar(cenario.condominioId);

        assertThat(lista).extracting(Exercicio::id).containsExactly("po:" + po.getId(), "coluna:" + po.getId());
        Exercicio atual = lista.getFirst();
        assertThat(atual.tipo()).isEqualTo(TipoExercicio.PO);
        assertThat(atual.rotulo()).isEqualTo("2026/2027");
        assertThat(atual.versao()).isEqualTo(1);
        assertThat(atual.previstoMes()).isEqualByComparingTo("451620.13");
        assertThat(atual.meses()).hasSize(12).allMatch(m -> m.situacao() == SituacaoMes.SEM_FLUXO && !m.prorrogado());
        assertThat(atual.depara()).isNotNull();
        assertThat(atual.rubricas().linhas()).isPositive();
        assertThat(atual.colunaImpressa()).isNull();

        Exercicio coluna = lista.get(1);
        assertThat(coluna.tipo()).isEqualTo(TipoExercicio.COLUNA_IMPRESSA);
        assertThat(coluna.rotulo()).isEqualTo("2025/2026 (coluna impressa)");
        assertThat(coluna.poId()).isEqualTo(po.getId());
        assertThat(coluna.inicio()).isEqualTo("2025-05");
        assertThat(coluna.fim()).isEqualTo("2026-04");
        assertThat(coluna.meses()).isEmpty();
        assertThat(coluna.depara()).isNull();
        assertThat(coluna.rubricas()).isNull();
        // Soma das linhas, como na PO confirmada (Q29): o 1.6 entra com 18.746,25
        assertThat(coluna.previstoMes()).isEqualByComparingTo("441525.22");
        assertThat(coluna.avisos()).containsExactly(AVISO_1_6, AVISO_TOTAL);
    }

    @Test
    void conferenciaDaColunaMostraGruposFundosELinha1320() {
        Budget po = confirmar(PoDoPiloto.padrao().comColunaAnterior(), "2026-05", "2027-04");

        ConferenciaColuna c = cenario.exercicios.colunaImpressa(cenario.condominioId, po.getId());

        assertThat(c.id()).isEqualTo("coluna:" + po.getId());
        assertThat(c.substituida()).isFalse();
        assertThat(c.poAnteriorId()).isNull();
        assertThat(c.totalImpresso()).isEqualByComparingTo("441304.38");
        assertThat(c.totalIncluiFundos()).isFalse();
        assertThat(c.fundos()).isEqualByComparingTo("22065.22");
        assertThat(c.previstoMes()).isEqualByComparingTo("441525.22");
        assertThat(c.grupos()).extracting(GrupoColuna::codigo, g -> g.valor().toPlainString(), GrupoColuna::confere)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("1.1", "37661.43", true),
                        org.assertj.core.groups.Tuple.tuple("1.2", "565.00", true),
                        org.assertj.core.groups.Tuple.tuple("1.3", "348631.55", true),
                        org.assertj.core.groups.Tuple.tuple("1.4", "0.00", true),
                        org.assertj.core.groups.Tuple.tuple("1.5", "2350.00", true),
                        org.assertj.core.groups.Tuple.tuple("1.6", "18746.25", false),
                        org.assertj.core.groups.Tuple.tuple("1.7", "19300.00", true),
                        org.assertj.core.groups.Tuple.tuple("1.8", "14270.99", true),
                        org.assertj.core.groups.Tuple.tuple("1.9", "22065.22", true));
        GrupoColuna administrativas = c.grupos().get(5);
        assertThat(administrativas.impresso()).isEqualByComparingTo("18525.42");
        assertThat(administrativas.diferenca()).isEqualByComparingTo("-220.83");
        assertThat(c.grupos().get(8).fundos()).isTrue();
        LinhaColuna sindicatura = c.grupos().get(2).linhas().stream().filter(l -> l.codigo().equals("1.3.20"))
                .findFirst().orElseThrow();
        assertThat(sindicatura.valor()).isEqualByComparingTo("17195.00");
        assertThat(sindicatura.percentualTexto()).isEqualTo("-53,47%");
        assertThat(cenario.linha(po, "1.3.20", 0).getBudgeted()).isEqualByComparingTo("8000.00");
        assertThat(c.diferencas()).isEmpty();
        assertThat(c.avisos()).containsExactly(AVISO_1_6, AVISO_TOTAL);
    }

    @Test
    void poAnteriorConfirmadaDepoisSubstituiAColunaComAvisoPorGrupo() {
        Budget atual = confirmar(PoDoPiloto.padrao().comColunaAnterior(), "2026-05", "2027-04");
        // Cópia de teste da PO do piloto confirmada como 2025/2026: Pessoal 69.193,86 pela soma das linhas
        Budget anterior = confirmar(PoDoPiloto.padrao(), "2025-05", "2026-04");

        List<Exercicio> lista = cenario.exercicios.listar(cenario.condominioId);

        assertThat(lista).extracting(Exercicio::id).containsExactly("po:" + atual.getId(), "po:" + anterior.getId());
        Exercicio exAnterior = lista.get(1);
        assertThat(exAnterior.rotulo()).isEqualTo("2025/2026");
        assertThat(exAnterior.colunaImpressa()).isEqualTo("coluna:" + atual.getId());
        assertThat(exAnterior.avisos()).contains("A coluna \"Orçado anterior\" difere da PO anterior enviada no grupo"
                + " 1.1 PESSOAL: 69.193,86 (PO enviada) × 37.661,43 (coluna impressa)");
        ConferenciaColuna c = cenario.exercicios.colunaImpressa(cenario.condominioId, atual.getId());
        assertThat(c.substituida()).isTrue();
        assertThat(c.poAnteriorId()).isEqualTo(anterior.getId());
        assertThat(c.poAnteriorRotulo()).isEqualTo("2025/2026");
        assertThat(c.diferencas()).extracting(ColunaImpressa.DiferencaGrupo::codigo).contains("1.1");
        assertThat(c.avisos()).contains(AVISO_1_6);
    }

    @Test
    void mesesMostramFluxoEProrrogacao() {
        Budget po = confirmar(PoDoPiloto.padrao(), "2026-05", "2027-04");
        cenario.fluxo("fluxo-2026-09.pdf", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 0);
        cenario.prorrogacao.extend(cenario.condominioId, po.getId(),
                new BudgetExtensionRequest("2027-05", "PO 2027/2028 ainda não aprovada"), "admin");

        Exercicio ex = cenario.exercicios.listar(cenario.condominioId).getFirst();

        assertThat(ex.meses()).hasSize(13);
        assertThat(ex.meses().get(4)).isEqualTo(new MesDoExercicio("2026-09", SituacaoMes.COM_FLUXO, false));
        assertThat(ex.meses().getLast()).isEqualTo(new MesDoExercicio("2027-05", SituacaoMes.SEM_FLUXO, true));
        assertThat(ex.prorrogacao().until()).isEqualTo("2027-05");
    }

    @Test
    void poSemAColunaNaoTemExercicioVirtual() {
        Budget po = confirmar(PoDoPiloto.padrao(), "2026-05", "2027-04");

        assertThat(cenario.exercicios.listar(cenario.condominioId)).extracting(Exercicio::id)
                .containsExactly("po:" + po.getId());
        assertThatThrownBy(() -> cenario.exercicios.colunaImpressa(cenario.condominioId, po.getId()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void poNaoConfirmadaNaoEntraNaLista() {
        cenario.lerPo(PoDoPiloto.padrao().comColunaAnterior());

        assertThat(cenario.exercicios.listar(cenario.condominioId)).isEmpty();
    }

    @Test
    void colunaConfereComTotalQueIncluiOsFundosSemAviso() {
        Budget po = confirmar(PoDoPiloto.padrao().comColunaAnterior().comTotalAnterior("463369.60"),
                "2026-05", "2027-04");

        ConferenciaColuna c = cenario.exercicios.colunaImpressa(cenario.condominioId, po.getId());

        assertThat(c.totalIncluiFundos()).isTrue();
        assertThat(c.avisos()).containsExactly(AVISO_1_6);
        assertThat(c.previstoMes()).isEqualByComparingTo(new BigDecimal("441525.22"));
    }

    private Budget confirmar(PoDoPiloto piloto, String inicio, String fim) {
        Budget po = cenario.lerPo(piloto);
        var p = cenario.pedidoDoPiloto(po);
        cenario.confirmacao.confirm(cenario.condominioId, po.getId(), new BudgetConfirmationRequest(inicio, fim,
                p.minutesFileId(), false, LocalDate.of(2026, 5, 20), p.effectiveCodes(), p.funds(), false, false,
                null), "admin");
        return po;
    }
}
