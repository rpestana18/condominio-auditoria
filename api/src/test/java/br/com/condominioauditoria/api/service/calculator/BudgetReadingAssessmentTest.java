package br.com.condominioauditoria.api.service.calculator;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.enums.BudgetStatus;
import br.com.condominioauditoria.api.orcamento.DinheiroBr;
import br.com.condominioauditoria.api.orcamento.PoDoPiloto;
import br.com.condominioauditoria.api.service.calculator.BudgetReadingAssessment.AssessedCheck;
import br.com.condominioauditoria.api.service.calculator.BudgetReadingAssessment.BudgetCheck;
import br.com.condominioauditoria.api.service.calculator.BudgetReadingAssessment.CheckClassification;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** RF-03.1.2: what the budget checks mean in the api, with the rounding tolerance. */
class BudgetReadingAssessmentTest {

    private static final BigDecimal ONE_CENT = new BigDecimal("0.01");
    private final Budget budget = new Budget(UUID.randomUUID(), UUID.randomUUID(),
            "e".repeat(64));

    @Test
    void pilotBudgetWithOneCentDifferencesIsReadWithWarnings() {
        PoDoPiloto pilot = PoDoPiloto.padrao();
        BudgetStructure structure = BudgetStructure.of(pilot.linhasGravadas(budget));

        var result = BudgetReadingAssessment.assess(structure, pilot.conferenciasParaAvaliacao(), ONE_CENT);

        assertThat(result.status()).isEqualTo(BudgetStatus.LIDA);
        assertThat(result.discrepancies()).isEmpty();
        assertThat(result.roundings()).containsExactly(
                "1.3 SERVIÇOS - CONTRATOS EFETIVOS impresso 336.274,17; soma das linhas 336.274,18; diferença de 0,01"
                        + " tratada como arredondamento; os cálculos usam a soma das linhas",
                "1.9 Fundos do Condomínio impresso 22.581,01; soma das linhas 22.581,00; diferença de 0,01"
                        + " tratada como arredondamento; os cálculos usam a soma das linhas");
        assertThat(result.hasRepeatedCode()).isTrue();
    }

    @Test
    void monthlyPlannedUsesLinesSum() {
        BudgetStructure structure = BudgetStructure.of(PoDoPiloto.padrao().linhasGravadas(budget));

        assertThat(structure.total().getBudgeted()).isEqualByComparingTo("474201.13");
        assertThat(structure.printedMonthlyPlanned()).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("451620.12"));
        // 1.3 adds up to 0.01 more in the lines than the printed subtotal
        assertThat(structure.monthlyPlannedFromLines()).isEqualByComparingTo("451620.13");
        assertThat(structure.funds()).hasValueSatisfying(f -> {
            assertThat(f.line().getPrintedCode()).isEqualTo("1.9");
            assertThat(f.lines()).extracting(BudgetLine::getPrintedCode).containsExactly("1.9.1", "1.9.2");
        });
    }

    @Test
    void withoutToleranceOneCentDifferencesAreDiscrepancy() {
        PoDoPiloto pilot = PoDoPiloto.padrao();

        var result = BudgetReadingAssessment.assess(BudgetStructure.of(pilot.linhasGravadas(budget)),
                pilot.conferenciasParaAvaliacao(), new BigDecimal("0.00"));

        assertThat(result.status()).isEqualTo(BudgetStatus.LIDA_COM_DIVERGENCIA);
        assertThat(result.discrepancies()).hasSize(2);
        assertThat(result.roundings()).isEmpty();
    }

    @Test
    void wrongPrintedSubtotalIsDiscrepancyWithBothSums() {
        PoDoPiloto pilot = PoDoPiloto.padrao().comSubtotalPessoal("69193.00");

        var result = BudgetReadingAssessment.assess(BudgetStructure.of(pilot.linhasGravadas(budget)),
                pilot.conferenciasParaAvaliacao(), ONE_CENT);

        assertThat(result.status()).isEqualTo(BudgetStatus.LIDA_COM_DIVERGENCIA);
        assertThat(result.discrepancies()).contains("1.1 PESSOAL impresso 69.193,00; soma das linhas 69.193,86",
                "Total impresso 474.201,13; soma dos grupos 474.200,27");
        assertThat(result.roundings()).hasSize(2);
    }

    @Test
    void repeatedCodeAloneIsNotDiscrepancy() {
        BudgetStructure structure = BudgetStructure.of(PoDoPiloto.padrao().linhasGravadas(budget));
        var checks = List.of(new BudgetCheck("CODIGO_REPETIDO", "x", false, "1.3.2 aparece 2 vezes"));

        var result = BudgetReadingAssessment.assess(structure, checks, ONE_CENT);

        assertThat(result.status()).isEqualTo(BudgetStatus.LIDA);
        assertThat(result.checks()).extracting(AssessedCheck::classification)
                .containsExactly(CheckClassification.CODIGO_REPETIDO);
    }

    @Test
    void failureNotConfirmedByRecomputedSumIsDiscrepancy() {
        BudgetStructure structure = BudgetStructure.of(PoDoPiloto.padrao().linhasGravadas(budget));
        // Group 1.1 matches by the saved lines, but the rag reported a failure: it is not rounding
        var checks = List.of(new BudgetCheck("SUBTOTAL_GRUPO", "1.1", false, "1.1 PESSOAL: não bate"));

        var result = BudgetReadingAssessment.assess(structure, checks, ONE_CENT);

        assertThat(result.status()).isEqualTo(BudgetStatus.LIDA_COM_DIVERGENCIA);
    }

    @Test
    void unknownFailedCheckIsDiscrepancy() {
        BudgetStructure structure = BudgetStructure.of(PoDoPiloto.padrao().linhasGravadas(budget));
        var checks = List.of(new BudgetCheck("LINHA_SEM_GRUPO", "x", false, "linhas antes do primeiro grupo: 1.0.1"));

        assertThat(BudgetReadingAssessment.assess(structure, checks, ONE_CENT).status())
                .isEqualTo(BudgetStatus.LIDA_COM_DIVERGENCIA);
    }

    @Test
    void moneyInBrazilianFormat() {
        assertThat(DinheiroBr.formatar(new BigDecimal("474201.13"))).isEqualTo("474.201,13");
        assertThat(DinheiroBr.formatar(new BigDecimal("0.01"))).isEqualTo("0,01");
        assertThat(DinheiroBr.formatar(new BigDecimal("-1585.1"))).isEqualTo("-1.585,10");
        assertThat(DinheiroBr.formatar(new BigDecimal("999"))).isEqualTo("999,00");
    }
}
