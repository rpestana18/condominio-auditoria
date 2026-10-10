package br.com.condominioauditoria.api.service.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.condominioauditoria.api.dto.request.budget.BudgetConfirmationRequest;
import br.com.condominioauditoria.api.dto.request.budget.EffectiveCodeRequest;
import br.com.condominioauditoria.api.dto.request.budget.FundLinkRequest;
import br.com.condominioauditoria.api.dto.response.budget.BudgetFundLineResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetWarningResponse;
import br.com.condominioauditoria.api.exception.BudgetConfirmationRejectedException;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetEvent;
import br.com.condominioauditoria.api.model.enums.BudgetStatus;
import br.com.condominioauditoria.api.model.enums.BudgetWarningCode;
import br.com.condominioauditoria.api.model.enums.FileCategory;
import br.com.condominioauditoria.api.model.enums.Severity;
import br.com.condominioauditoria.api.service.audit.rule.ReserveFundCapRule;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** RF-03.1.3, RF-03.1.2 and Q29: budget confirmation by the Admin. */
class BudgetConfirmationServiceTest {

    private final BudgetScenario scenario = new BudgetScenario();

    @Test
    void confirmsPilotBudgetWithFiscalYearMinutesEffectiveCodeAndFunds() {
        Budget budget = scenario.readBudget(PilotBudget.defaults());

        var detail = scenario.confirmation.confirm(scenario.condominiumId, budget.getId(),
                scenario.pilotRequest(budget), "admin");

        assertThat(budget.getStatus()).isEqualTo(BudgetStatus.CONFIRMED);
        assertThat(budget.getVersion()).isEqualTo(1);
        assertThat(budget.getFiscalYearStart()).isEqualTo(YearMonth.of(2026, 5));
        assertThat(budget.getFiscalYearEnd()).isEqualTo(YearMonth.of(2027, 4));
        assertThat(budget.getMinutesFileId()).isEqualTo(scenario.minutes.getId());
        assertThat(budget.isWithoutMinutes()).isFalse();
        assertThat(budget.getConfirmedBy()).isEqualTo("admin");
        assertThat(budget.isDiscrepancyAcknowledged()).isFalse();
        // Neither of the two 1.3.2 lines is dropped; the second one gets the effective code
        assertThat(scenario.line(budget, "1.3.2", 0).getEffectiveCode()).isEqualTo("1.3.2");
        assertThat(scenario.line(budget, "1.3.2", 1).getEffectiveCode()).isEqualTo("1.3.25");
        assertThat(scenario.line(budget, "1.3.2", 1).getBudgeted()).isEqualByComparingTo("1518.93");
        assertThat(detail.repeatedCodes()).singleElement().satisfies(r -> assertThat(r.resolved()).isTrue());

        assertThat(detail.funds()).extracting(BudgetFundLineResponse::effectiveCode,
                BudgetFundLineResponse::fund).containsExactly(
                org.assertj.core.groups.Tuple.tuple("1.9.1", "FUNDO DE RESERVA"),
                org.assertj.core.groups.Tuple.tuple("1.9.2", "OBRAS / REFORMAS / INFRA"));
        assertThat(detail.funds()).extracting(BudgetFundLineResponse::budgeted)
                .usingElementComparator(java.math.BigDecimal::compareTo)
                .containsExactly(new java.math.BigDecimal("13548.60"), new java.math.BigDecimal("9032.40"));

        assertThat(scenario.budgetEvents).singleElement().satisfies(e -> {
            assertThat(e.getType()).isEqualTo(BudgetEvent.CONFIRMED);
            assertThat(e.getUsername()).isEqualTo("admin");
            assertThat(e.getOccurredAt()).isNotNull();
            assertThat(e.getDetail()).contains("Exercício 2026-05 a 2027-04", "1.3.2 (ordem 12, Caixa D'água) → 1.3.25",
                    "1.9.1 Fundo de Reserva → FUNDO DE RESERVA", "1.9.2 Fundo de Obras → OBRAS / REFORMAS / INFRA",
                    "Diferenças tratadas como arredondamento");
        });
    }

    @Test
    void budgetApprovedInMayOnlyWarnsWithoutFinding() {
        Budget budget = scenario.readBudget(PilotBudget.defaults());

        var detail = scenario.confirmation.confirm(scenario.condominiumId, budget.getId(),
                scenario.pilotRequest(budget), "admin");

        assertThat(detail.warnings()).extracting(BudgetWarningResponse::text)
                .contains("PO aprovada fora do 1º trimestre (Conv. 10.2)");
        assertThat(detail.findings()).isEmpty();
        assertThat(scenario.findings).isEmpty();
        // The roundings of 1.3 and 1.9 stay visible as warnings
        assertThat(detail.warnings()).filteredOn(a -> a.code() == BudgetWarningCode.ROUNDING).hasSize(2);
    }

    @Test
    void budgetApprovedInFirstQuarterHasNoWarning() {
        Budget budget = scenario.readBudget(PilotBudget.defaults());
        var p = scenario.pilotRequest(budget);
        var request = new BudgetConfirmationRequest("2026-03", "2027-02", p.minutesFileId(), false, LocalDate.of(2026,
                3, 15),
                p.effectiveCodes(), p.funds(), false, false, null);

        var detail = scenario.confirmation.confirm(scenario.condominiumId, budget.getId(), request, "admin");

        assertThat(detail.warnings()).extracting(BudgetWarningResponse::code).doesNotContain(BudgetWarningCode.OUTSIDE_FIRST_QUARTER);
    }

    @Test
    void pilotFundsOf3And2PercentCreateNoFinding() {
        Budget budget = scenario.readBudget(PilotBudget.defaults());

        scenario.confirmation.confirm(scenario.condominiumId, budget.getId(), scenario.pilotRequest(budget), "admin");

        assertThat(scenario.findings).isEmpty();
    }

    @Test
    void reserveFundAbove5PercentCreatesAttentionFindingWithEvidence() {
        Budget budget = scenario.readBudget(PilotBudget.defaults().withReserveFund("25000.00"));
        assertThat(budget.getStatus()).isEqualTo(BudgetStatus.READ);

        var detail = scenario.confirmation.confirm(scenario.condominiumId, budget.getId(),
                scenario.pilotRequest(budget), "admin");

        assertThat(scenario.findings).singleElement().satisfies(a -> {
            assertThat(a.getRule()).isEqualTo(ReserveFundCapRule.CODE);
            assertThat(a.getRuleVersion()).isEqualTo(ReserveFundCapRule.VERSION);
            assertThat(a.getSeverity()).isEqualTo(Severity.WARNING);
            assertThat(a.getReferenceMonth()).isEqualTo(YearMonth.of(2026, 5));
            assertThat(a.getDescription()).isEqualTo("Fundo de reserva previsto na PO (linha 1.9.1): 25.000,00 por mês,"
                    + " 5,5% do previsto do mês (451.620,13). O teto da Conv. 20.1 é 5%. Verificar a ata que aprovou a PO.");
        });
        assertThat(scenario.evidence).singleElement().satisfies(e -> {
            assertThat(e.getFileId()).isEqualTo(budget.getFileId());
            assertThat(e.getSha256()).isEqualTo(budget.getSha256());
            assertThat(e.getPage()).isEqualTo(1);
            assertThat(e.getBudgetLineId()).isEqualTo(scenario.line(budget, "1.9.1", 0).getId());
        });
        assertThat(detail.findings()).singleElement().satisfies(a -> assertThat(a.severity()).isEqualTo("WARNING"));
    }

    @Test
    void withoutCapRuleIsNotAssessedAndShowsAsWarning() {
        scenario.ruleParameters.clear();
        Budget budget = scenario.readBudget(PilotBudget.defaults().withReserveFund("25000.00"));

        var detail = scenario.confirmation.confirm(scenario.condominiumId, budget.getId(),
                scenario.pilotRequest(budget), "admin");

        assertThat(scenario.findings).isEmpty();
        assertThat(detail.warnings()).filteredOn(a -> a.code() == BudgetWarningCode.RULE_NOT_EVALUATED).singleElement()
                .satisfies(a -> assertThat(a.text()).contains("teto não cadastrado"));
    }

    @Test
    void repeatedCodeWithoutEffectiveCodeRejectsConfirmation() {
        Budget budget = scenario.readBudget(PilotBudget.defaults());
        var p = scenario.pilotRequest(budget);
        var withoutCode = new BudgetConfirmationRequest(p.fiscalYearStart(), p.fiscalYearEnd(), p.minutesFileId(),
                false,
                p.approvalDate(), List.of(), p.funds(), false, false, null);

        assertThatThrownBy(() -> scenario.confirmation.confirm(scenario.condominiumId, budget.getId(), withoutCode,
                "admin"))
                .isInstanceOfSatisfying(BudgetConfirmationRejectedException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(e.reasons()).singleElement().asString()
                            .startsWith("Código repetido sem código efetivo distinto: 1.3.2 (ordens 8, 12)");
                });
        assertThat(budget.getStatus()).isEqualTo(BudgetStatus.READ);
        assertThat(scenario.line(budget, "1.3.2", 1).getEffectiveCode()).isEqualTo("1.3.2");
        assertThat(scenario.budgetEvents).isEmpty();
        assertThat(scenario.fundLinks).isEmpty();
    }

    @Test
    void effectiveCodeOutsideGroupOrOnNonRepeatedLineIsRejected() {
        Budget budget = scenario.readBudget(PilotBudget.defaults());
        var p = scenario.pilotRequest(budget);
        var request = new BudgetConfirmationRequest(p.fiscalYearStart(), p.fiscalYearEnd(), p.minutesFileId(), false,
                p.approvalDate(), List.of(
                        new EffectiveCodeRequest(scenario.line(budget, "1.3.2", 1).getId(), "1.7.25"),
                        new EffectiveCodeRequest(scenario.line(budget, "1.3.20", 0).getId(), "1.3.26")),
                p.funds(), false, false, null);

        assertThatThrownBy(() -> scenario.confirmation.confirm(scenario.condominiumId, budget.getId(), request,
                "admin"))
                .isInstanceOfSatisfying(BudgetConfirmationRejectedException.class,
                        e -> assertThat(e.reasons()).anySatisfy(
                        m -> assertThat(m).contains("\"1.7.25\" inválido")).anySatisfy(
                        m -> assertThat(m).contains("1.3.20 não se repete")));
    }

    @Test
    void effectiveCodeEqualToAnotherCodeIsStillRepeated() {
        Budget budget = scenario.readBudget(PilotBudget.defaults());
        var p = scenario.pilotRequest(budget);
        var request = new BudgetConfirmationRequest(p.fiscalYearStart(), p.fiscalYearEnd(), p.minutesFileId(), false,
                p.approvalDate(), List.of(new EffectiveCodeRequest(scenario.line(budget, "1.3.2", 1).getId(),
                        "1.3.20")), p.funds(), false, false, null);

        assertThatThrownBy(() -> scenario.confirmation.confirm(scenario.condominiumId, budget.getId(), request,
                "admin"))
                .isInstanceOfSatisfying(BudgetConfirmationRejectedException.class, e -> assertThat(e.reasons())
                        .anySatisfy(m -> assertThat(m).startsWith("Código repetido sem código efetivo distinto: 1.3.20")));
    }

    @Test
    void budgetWithDiscrepancyIsConfirmedOnlyAcknowledgedWithJustification() {
        Budget budget = scenario.readBudget(PilotBudget.defaults().withStaffSubtotal("69193.00"));
        assertThat(budget.getStatus()).isEqualTo(BudgetStatus.READ_WITH_DISCREPANCY);
        var p = scenario.pilotRequest(budget);

        assertThatThrownBy(() -> scenario.confirmation.confirm(scenario.condominiumId, budget.getId(), p, "admin"))
                .isInstanceOfSatisfying(BudgetConfirmationRejectedException.class,
                        e -> assertThat(e.reasons()).singleElement()
                        .asString().contains("1.1 PESSOAL impresso 69.193,00; soma das linhas 69.193,86")
                        .contains("ciente da divergência"));

        var withoutJustification = acknowledged(p, "  ");
        assertThatThrownBy(() -> scenario.confirmation.confirm(scenario.condominiumId, budget.getId(),
                withoutJustification, "admin"))
                .isInstanceOfSatisfying(BudgetConfirmationRejectedException.class, e -> assertThat(e.reasons())
                        .containsExactly("A confirmação ciente da divergência exige justificativa."));
        assertThat(budget.getStatus()).isEqualTo(BudgetStatus.READ_WITH_DISCREPANCY);

        var detail = scenario.confirmation.confirm(scenario.condominiumId, budget.getId(),
                acknowledged(p, "Subtotal impresso errado no documento aprovado; linhas conferidas no PDF"), "admin");

        assertThat(budget.getStatus()).isEqualTo(BudgetStatus.CONFIRMED);
        assertThat(budget.isDiscrepancyAcknowledged()).isTrue();
        // Calculations use the lines' sum: the wrong printed subtotal does not enter the monthly planned amount
        assertThat(budget.getMonthlyPlanned()).isEqualByComparingTo("451620.13");
        assertThat(detail.warnings()).filteredOn(a -> a.code() == BudgetWarningCode.CONFIRMED_WITH_DISCREPANCY)
                .singleElement().satisfies(a -> assertThat(a.text())
                        .startsWith("PO confirmada com divergência: 1.1 PESSOAL impresso 69.193,00; soma das linhas 69.193,86"));
        assertThat(detail.findings()).isEmpty();
        assertThat(scenario.budgetEvents).singleElement().satisfies(e -> {
            assertThat(e.getUsername()).isEqualTo("admin");
            assertThat(e.getJustification()).isEqualTo("Subtotal impresso errado no documento aprovado; linhas conferidas no PDF");
            assertThat(e.getDetail()).contains("Conferências que falharam (confirmada ciente da divergência): "
                    + "1.1 PESSOAL impresso 69.193,00; soma das linhas 69.193,86");
        });
    }

    @Test
    void acknowledgedDoesNotWaiveEffectiveCode() {
        Budget budget = scenario.readBudget(PilotBudget.defaults().withStaffSubtotal("69193.00"));
        var p = scenario.pilotRequest(budget);
        var acknowledgedWithoutCode = new BudgetConfirmationRequest(p.fiscalYearStart(), p.fiscalYearEnd(),
                p.minutesFileId(), false,
                p.approvalDate(), List.of(), p.funds(), false, true, "Erro de soma no documento");

        assertThatThrownBy(() -> scenario.confirmation.confirm(scenario.condominiumId, budget.getId(),
                acknowledgedWithoutCode, "admin"))
                .isInstanceOfSatisfying(BudgetConfirmationRejectedException.class,
                        e -> assertThat(e.reasons()).singleElement()
                        .asString().contains("código repetido não é divergência de soma"));
        assertThat(budget.getStatus()).isEqualTo(BudgetStatus.READ_WITH_DISCREPANCY);
    }

    @Test
    void withoutMinutesIsMarkedPending() {
        Budget budget = scenario.readBudget(PilotBudget.defaults());
        var p = scenario.pilotRequest(budget);
        var withoutMinutes = new BudgetConfirmationRequest("2026-05", "2027-04", null, true, null, p.effectiveCodes(),
                p.funds(),
                false, false, null);

        var detail = scenario.confirmation.confirm(scenario.condominiumId, budget.getId(), withoutMinutes, "admin");

        assertThat(budget.isWithoutMinutes()).isTrue();
        assertThat(detail.warnings()).extracting(BudgetWarningResponse::code)
                .contains(BudgetWarningCode.NO_MINUTES, BudgetWarningCode.OUTSIDE_FIRST_QUARTER);
    }

    @Test
    void minutesMustBeMinutesCategoryWithDate() {
        Budget budget = scenario.readBudget(PilotBudget.defaults());
        var p = scenario.pilotRequest(budget);
        var contract = scenario.file(FileCategory.CONTRACT, "contrato.pdf");
        var request = new BudgetConfirmationRequest("2026-05", "2027-04", contract.getId(), false, null,
                p.effectiveCodes(),
                p.funds(), false, false, null);

        assertThatThrownBy(() -> scenario.confirmation.confirm(scenario.condominiumId, budget.getId(), request,
                "admin"))
                .isInstanceOfSatisfying(BudgetConfirmationRejectedException.class,
                        e -> assertThat(e.reasons()).containsExactly(
                        "O arquivo \"contrato.pdf\" não está na categoria \"Atas de assembleia\".",
                        "Informe a data da assembleia que aprovou a PO."));
    }

    @Test
    void fiscalYearAndFundsRequired() {
        Budget budget = scenario.readBudget(PilotBudget.defaults());
        var p = scenario.pilotRequest(budget);
        var request = new BudgetConfirmationRequest("2026-13", null, p.minutesFileId(), false, p.approvalDate(),
                p.effectiveCodes(), List.of(new FundLinkRequest(scenario.line(budget, "1.9.1", 0).getId(),
                        scenario.operatingFund.getId())), false, false, null);

        assertThatThrownBy(() -> scenario.confirmation.confirm(scenario.condominiumId, budget.getId(), request,
                "admin"))
                .isInstanceOfSatisfying(BudgetConfirmationRejectedException.class,
                        e -> assertThat(e.reasons()).containsExactly(
                        "O início do exercício deve estar no formato AAAA-MM: 2026-13",
                        "Informe o fim do exercício (AAAA-MM).",
                        "O fundo \"CONDOMÍNIO\" é o fundo ordinário e não pode ser ligado à linha 1.9.1.",
                        "Ligue a linha 1.9.2 Fundo de Obras a um fundo do fluxo."));
    }

    @Test
    void oneBudgetPerMonthWithoutReapprovalRejects() {
        Budget first = scenario.readBudget(PilotBudget.defaults());
        scenario.confirmation.confirm(scenario.condominiumId, first.getId(), scenario.pilotRequest(first), "admin");
        Budget second = scenario.readBudget(PilotBudget.defaults());

        assertThatThrownBy(() -> scenario.confirmation.confirm(scenario.condominiumId, second.getId(),
                scenario.pilotRequest(second), "admin"))
                .isInstanceOfSatisfying(BudgetConfirmationRejectedException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).contains("versão 1 (2026-05 a 2027-04)", "Só uma PO vale para cada mês");
                });
        assertThat(second.getStatus()).isEqualTo(BudgetStatus.READ);
    }

    @Test
    void reapprovalSupersedesFromNewVersionStart() {
        Budget first = scenario.readBudget(PilotBudget.defaults());
        scenario.confirmation.confirm(scenario.condominiumId, first.getId(), scenario.pilotRequest(first), "admin");
        Budget second = scenario.readBudget(PilotBudget.defaults());
        var p = scenario.pilotRequest(second);
        var reapproval = new BudgetConfirmationRequest("2026-09", "2027-04", p.minutesFileId(), false,
                LocalDate.of(2026, 8, 30),
                p.effectiveCodes(), p.funds(), true, false, null);

        scenario.confirmation.confirm(scenario.condominiumId, second.getId(), reapproval, "admin");

        assertThat(first.getStatus()).isEqualTo(BudgetStatus.SUPERSEDED);
        assertThat(first.getSupersededFrom()).isEqualTo(YearMonth.of(2026, 9));
        assertThat(second.getVersion()).isEqualTo(2);
        assertThat(scenario.budgetOfMonth(YearMonth.of(2026, 8))).contains(first);
        assertThat(scenario.budgetOfMonth(YearMonth.of(2026, 9))).contains(second);
        assertThat(scenario.budgetEvents).extracting(BudgetEvent::getType).containsExactly(BudgetEvent.CONFIRMED,
                BudgetEvent.SUPERSEDED, BudgetEvent.CONFIRMED);
    }

    @Test
    void reapprovalEndingBeforePreviousIsRejected() {
        Budget first = scenario.readBudget(PilotBudget.defaults());
        scenario.confirmation.confirm(scenario.condominiumId, first.getId(), scenario.pilotRequest(first), "admin");
        Budget second = scenario.readBudget(PilotBudget.defaults());
        var p = scenario.pilotRequest(second);
        var shortOne = new BudgetConfirmationRequest("2026-09", "2026-12", p.minutesFileId(), false, LocalDate.of(2026,
                8, 30),
                p.effectiveCodes(), p.funds(), true, false, null);

        assertThatThrownBy(() -> scenario.confirmation.confirm(scenario.condominiumId, second.getId(), shortOne,
                "admin"))
                .isInstanceOfSatisfying(BudgetConfirmationRejectedException.class,
                        e -> assertThat(e.getMessage()).contains("precisa cobrir até 2027-04"));
        assertThat(first.getStatus()).isEqualTo(BudgetStatus.CONFIRMED);
    }

    @Test
    void alreadyConfirmedBudgetIsNotConfirmedAgain() {
        Budget budget = scenario.readBudget(PilotBudget.defaults());
        scenario.confirmation.confirm(scenario.condominiumId, budget.getId(), scenario.pilotRequest(budget), "admin");

        assertThatThrownBy(() -> scenario.confirmation.confirm(scenario.condominiumId, budget.getId(),
                scenario.pilotRequest(budget), "admin"))
                .isInstanceOfSatisfying(BudgetConfirmationRejectedException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));
    }

    private static BudgetConfirmationRequest acknowledged(BudgetConfirmationRequest p, String justification) {
        return new BudgetConfirmationRequest(p.fiscalYearStart(), p.fiscalYearEnd(), p.minutesFileId(),
                p.withoutMinutes(),
                p.approvalDate(), p.effectiveCodes(), p.funds(), false, true, justification);
    }
}
