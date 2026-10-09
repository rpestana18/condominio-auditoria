package br.com.condominioauditoria.api.orcamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.condominioauditoria.api.dto.request.budget.BudgetConfirmationRequest;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.Enrichment;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.LedgerEntryData;
import br.com.condominioauditoria.api.model.accounting.LedgerEntry;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.orcamento.ComparacaoExercicios.GrupoComparado;
import br.com.condominioauditoria.api.orcamento.ComparacaoExercicios.Resultado;
import br.com.condominioauditoria.api.orcamento.ComparacaoExercicios.ResumoExercicio;
import br.com.condominioauditoria.api.orcamento.ComparacaoExercicios.RubricaComparada;
import br.com.condominioauditoria.api.orcamento.ComparacaoExercicios.ValorComparado;
import br.com.condominioauditoria.api.orcamento.ComparacaoExercicios.Variacao;
import br.com.condominioauditoria.api.orcamento.ExercicioDtos.TipoExercicio;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * RF-11.6 (ADR 0005, Decisão 2): comparação de exercícios. PO 2026/2027 do piloto com a coluna "Orçado anterior" real
 * e, para dois exercícios com PO, a cópia de teste da PO do piloto confirmada como 05/2025 a 04/2026.
 */
class ComparacaoExerciciosTest {

    private final CenarioPo cenario = new CenarioPo();

    @Test
    void semPoAnteriorComparaComAColunaImpressa() {
        Budget po = confirmar(PoDoPiloto.padrao().comColunaAnterior(), "2026-05", "2027-04");

        Resultado r = cenario.comparacao.comparar(cenario.condominioId, null, null, false);

        assertThat(r.exercicios()).extracting(e -> e.id(), e -> e.tipo(), e -> e.rotulo()).containsExactly(
                org.assertj.core.groups.Tuple.tuple("po:" + po.getId(), TipoExercicio.PO, "2026/2027"),
                org.assertj.core.groups.Tuple.tuple("coluna:" + po.getId(), TipoExercicio.COLUNA_IMPRESSA,
                        "2025/2026 (coluna impressa)"));
        ResumoExercicio atual = r.resumo().getFirst();
        ResumoExercicio coluna = r.resumo().get(1);
        assertThat(atual.previstoMes()).isEqualByComparingTo("451620.13");
        assertThat(atual.previstoExercicio()).isEqualByComparingTo("5419441.56");
        assertThat(coluna.previstoMes()).isEqualByComparingTo("441525.22");
        // Previsto do mês pela soma das linhas nos dois lados (Q29): +10.094,91 e +2,3%
        assertThat(atual.variacaoPrevistoMes()).isEqualTo(new Variacao(new BigDecimal("10094.91"),
                new BigDecimal("2.3"), false));
        assertThat(coluna.variacaoPrevistoMes()).isNull();
        // Sem fluxo carregado: só previsto, nenhum realizado R$ 0,00
        assertThat(atual.realizado()).isNull();
        assertThat(atual.mesesComFluxo()).isZero();
        assertThat(coluna.realizado()).isNull();
        assertThat(coluna.achadosAbertos()).isNull();

        GrupoComparado contratos = grupo(r, "1.3");
        assertThat(contratos.valores().getFirst().previstoMes()).isEqualByComparingTo("336274.18");
        assertThat(contratos.valores().get(1).previstoMes()).isEqualByComparingTo("348631.55");
        assertThat(contratos.valores().getFirst().variacaoPrevistoMes()).isEqualTo(
                new Variacao(new BigDecimal("-12357.37"), new BigDecimal("-3.5"), false));
        assertThat(grupo(r, "1.9").fundos()).isTrue();
        assertThat(grupo(r, "1.9").valores().get(1).previstoMes()).isEqualByComparingTo("22065.22");
    }

    @Test
    void linhaPorRubricaComNovaNoExercicio() {
        confirmar(PoDoPiloto.padrao().comColunaAnterior(), "2026-05", "2027-04");

        Resultado r = cenario.comparacao.comparar(cenario.condominioId, null, null, false);

        ValorComparado sindicatura = rubricaDaLinha(r, "1.3.20").valores().getFirst();
        assertThat(sindicatura.previstoMes()).isEqualByComparingTo("8000.00");
        assertThat(rubricaDaLinha(r, "1.3.20").valores().get(1).previstoMes()).isEqualByComparingTo("17195.00");
        assertThat(sindicatura.variacaoPrevistoMes()).isEqualTo(new Variacao(new BigDecimal("-9195.00"),
                new BigDecimal("-53.5"), false));
        ValorComparado caixa = rubricaDaLinha(r, "1.3.25").valores().getFirst();
        assertThat(caixa.previstoMes()).isEqualByComparingTo("1518.93");
        assertThat(caixa.variacaoPrevistoMes()).isEqualTo(new Variacao(new BigDecimal("1518.93"), null, true));
        // A primeira PO confirmada deu rubrica a todas as linhas: nada fica sem correspondência
        assertThat(r.semCorrespondencia()).isEmpty();
    }

    @Test
    void linhaSemRubricaConfirmadaFicaSemCorrespondenciaENaoESomada() {
        Budget po = confirmar(PoDoPiloto.padrao().comColunaAnterior(), "2026-05", "2027-04");
        BudgetLine sindicatura = cenario.linha(po, "1.3.20", 0);
        cenario.linhasRubrica.removeIf(l -> l.getBudgetLineId().equals(sindicatura.getId()));

        Resultado r = cenario.comparacao.comparar(cenario.condominioId, null, null, false);

        assertThat(r.linhas()).noneMatch(x -> x.valores().stream().flatMap(v -> v.linhas().stream())
                .anyMatch(l -> l.codigo().equals("1.3.20")));
        assertThat(r.semCorrespondencia()).extracting(l -> l.exercicioId(), l -> l.codigo(),
                l -> l.previstoMes().toPlainString()).containsExactly(
                org.assertj.core.groups.Tuple.tuple("po:" + po.getId(), "1.3.20", "8000.00"),
                org.assertj.core.groups.Tuple.tuple("coluna:" + po.getId(), "1.3.20", "17195.00"));
        assertThat(r.avisos()).contains("2026/2027: 1 linha sem rubrica confirmada (bloco \"sem correspondência\")");
    }

    @Test
    void mesmosMesesSomaSoSetembroNosDoisExercicios() {
        Budget anterior = confirmar(PoDoPiloto.padrao(), "2025-05", "2026-04");
        Budget atual = confirmar(PoDoPiloto.padrao(), "2026-05", "2027-04");
        debito(cenario.fluxo("fluxo-2025-09.pdf", LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 30), 1), "1000.00",
                LocalDate.of(2025, 9, 10));
        debito(cenario.fluxo("fluxo-2026-09.pdf", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 1), "1500.00",
                LocalDate.of(2026, 9, 10));
        debito(cenario.fluxo("fluxo-2026-10.pdf", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 1), "700.00",
                LocalDate.of(2026, 10, 10));
        List<String> ids = List.of("po:" + anterior.getId(), "po:" + atual.getId());

        Resultado todos = cenario.comparacao.comparar(cenario.condominioId, ids, null, false);
        Resultado mesmos = cenario.comparacao.comparar(cenario.condominioId, ids, null, true);

        assertThat(todos.exercicios()).extracting(e -> e.id()).containsExactly("po:" + atual.getId(),
                "po:" + anterior.getId());
        assertThat(todos.comparando()).isNull();
        assertThat(todos.resumo().getFirst().mesesComFluxo()).isEqualTo(2);
        assertThat(todos.resumo().getFirst().realizado()).isEqualByComparingTo("2200.00");
        assertThat(todos.resumo().getFirst().previsto()).isEqualByComparingTo("903240.26");

        assertThat(mesmos.comparando()).isEqualTo("comparando: setembro");
        assertThat(mesmos.exercicios().getFirst().meses()).containsExactly("2026-09");
        assertThat(mesmos.exercicios().getFirst().periodo()).isEqualTo("2026-09");
        ResumoExercicio atualSet = mesmos.resumo().getFirst();
        ResumoExercicio anteriorSet = mesmos.resumo().get(1);
        assertThat(atualSet.mesesComFluxo()).isEqualTo(1);
        assertThat(atualSet.realizado()).isEqualByComparingTo("1500.00");
        assertThat(atualSet.previsto()).isEqualByComparingTo("451620.13");
        assertThat(anteriorSet.realizado()).isEqualByComparingTo("1000.00");
        assertThat(atualSet.variacaoRealizado()).isEqualTo(new Variacao(new BigDecimal("500.00"),
                new BigDecimal("50.0"), false));
        assertThat(atualSet.achadosAbertos()).isNotNull();
    }

    @Test
    void filtroDoFundoDeReservaMostraSoALinhaDele() {
        Budget po = confirmar(PoDoPiloto.padrao().comColunaAnterior(), "2026-05", "2027-04");

        Resultado r = cenario.comparacao.comparar(cenario.condominioId, null, cenario.reserva.getId(), false);

        assertThat(r.grupos()).extracting(GrupoComparado::codigo).containsExactly("1.9");
        assertThat(r.grupos().getFirst().valores()).allMatch(v -> v.linhas().size() == 1);
        assertThat(r.resumo().getFirst().previstoMes()).isEqualByComparingTo("13548.60");
        assertThat(r.resumo().get(1).previstoMes()).isEqualByComparingTo("13239.13");

        Resultado condominio = cenario.comparacao.comparar(cenario.condominioId, null, cenario.ordinario.getId(),
                false);
        assertThat(condominio.grupos()).extracting(GrupoComparado::codigo).doesNotContain("1.9");
        assertThat(condominio.resumo().getFirst().previstoMes()).isEqualByComparingTo("451620.13");
        assertThat(po).isNotNull();
    }

    @Test
    void pedidoInvalidoERecusado() {
        Budget po = confirmar(PoDoPiloto.padrao(), "2026-05", "2027-04");

        assertStatus(() -> cenario.comparacao.comparar(cenario.condominioId, null, null, false),
                HttpStatus.UNPROCESSABLE_CONTENT);
        assertStatus(() -> cenario.comparacao.comparar(cenario.condominioId, List.of("po:abc", "po:" + po.getId()),
                null, false), HttpStatus.BAD_REQUEST);
        assertStatus(() -> cenario.comparacao.comparar(cenario.condominioId,
                List.of("coluna:" + po.getId(), "po:" + po.getId()), null, false), HttpStatus.NOT_FOUND);
        assertStatus(() -> cenario.comparacao.comparar(cenario.condominioId,
                List.of("po:" + java.util.UUID.randomUUID(), "po:" + po.getId()), null, false), HttpStatus.NOT_FOUND);
    }

    @Test
    void variacaoEMesesComparados() {
        assertThat(ComparacaoExercicios.variacao(new BigDecimal("451620.13"), new BigDecimal("441525.22")))
                .isEqualTo(new Variacao(new BigDecimal("10094.91"), new BigDecimal("2.3"), false));
        assertThat(ComparacaoExercicios.variacao(new BigDecimal("0.00"), new BigDecimal("0.00")))
                .isEqualTo(new Variacao(new BigDecimal("0.00"), null, false));
        assertThat(ComparacaoExercicios.variacao(null, BigDecimal.ONE)).isNull();
        assertThat(ComparacaoExercicios.comparando(Set.of(Month.SEPTEMBER, Month.AUGUST)))
                .isEqualTo("comparando: agosto e setembro");
        assertThat(ComparacaoExercicios.comparando(Set.of())).isEqualTo(
                "comparando: nenhum mês com fluxo em todos os exercícios");
    }

    private static GrupoComparado grupo(Resultado r, String codigo) {
        return r.grupos().stream().filter(g -> g.codigo().equals(codigo)).findFirst().orElseThrow();
    }

    private static RubricaComparada rubricaDaLinha(Resultado r, String codigo) {
        return r.linhas().stream().filter(x -> x.valores().getFirst().linhas().stream()
                .anyMatch(l -> l.codigo().equals(codigo))).findFirst().orElseThrow();
    }

    private static void assertStatus(org.assertj.core.api.ThrowableAssert.ThrowingCallable chamada,
            HttpStatus status) {
        assertThatThrownBy(chamada).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(status));
    }

    private Budget confirmar(PoDoPiloto piloto, String inicio, String fim) {
        Budget po = cenario.lerPo(piloto);
        var p = cenario.pedidoDoPiloto(po);
        cenario.confirmacao.confirm(cenario.condominioId, po.getId(), new BudgetConfirmationRequest(inicio, fim,
                p.minutesFileId(), false, LocalDate.of(2026, 5, 20), p.effectiveCodes(), p.funds(), false, false,
                null), "admin");
        return po;
    }

    private void debito(SourceFile fluxo, String valor, LocalDate data) {
        var lido = new LedgerEntryData(1, cenario.lancamentos.size() + 1, data, "9999", "Teste", "", "Teste",
                BigDecimal.ZERO.setScale(2), new BigDecimal(valor), BigDecimal.ZERO.setScale(2),
                new Enrichment(null, null, null, false, false));
        cenario.lancamentos.add(new LedgerEntry(cenario.condominioId, fluxo.getId(), cenario.ordinario.getId(), lido));
    }
}
