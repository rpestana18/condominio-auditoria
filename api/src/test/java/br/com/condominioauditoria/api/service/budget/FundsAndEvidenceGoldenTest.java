package br.com.condominioauditoria.api.service.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import br.com.condominioauditoria.api.dto.request.budget.BudgetFundsRequest;
import br.com.condominioauditoria.api.dto.request.budget.FundLinkRequest;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualGroupResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualLineResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualWarningResponse;
import br.com.condominioauditoria.api.dto.response.budget.EvidenceResponse;
import br.com.condominioauditoria.api.dto.response.budget.FundResultResponse;
import br.com.condominioauditoria.api.event.BudgetChanged;
import br.com.condominioauditoria.api.exception.BudgetConfirmationRejectedException;
import br.com.condominioauditoria.api.model.budget.BudgetEvent;
import br.com.condominioauditoria.api.model.enums.FundComparisonStatus;
import br.com.condominioauditoria.api.report.BudgetVsActualExcelReport;
import br.com.condominioauditoria.api.report.BudgetVsActualPdfReport;
import br.com.condominioauditoria.api.report.BudgetVsActualReport;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/**
 * Frontend gaps on approved RFs, with September/2026 of the pilot (private golden): fund filter (RF-03.1.13), group
 * and total evidence (RF-03.1.12) and change of the fund links after confirmation, with a trail (RF-03.1.9). Skipped
 * without data/golden/privado.
 */
class FundsAndEvidenceGoldenTest {

    private static final String SEPTEMBER = "2026-09";

    @Test
    void groupAndTotalEvidenceSumScreenNumbers() {
        SeptemberGolden g = golden();
        g.confirmMap();
        BudgetVsActualResponse r = query(g, null);

        BudgetVsActualGroupResponse contracts = r.groups().stream().filter(x -> x.code().equals("1.3")).findFirst().orElseThrow();
        List<EvidenceResponse> ofGroup = evidence(g, BudgetVsActualCalculator.groupTarget(contracts.lineId()));
        List<EvidenceResponse> total = evidence(g, BudgetVsActualCalculator.TARGET_TOTAL);

        assertThat(sum(ofGroup)).isEqualByComparingTo(contracts.actual()).isEqualByComparingTo("341277.13");
        assertThat(ofGroup).hasSize(contracts.lines().stream().mapToInt(BudgetVsActualLineResponse::entries)
                .sum());
        assertThat(sum(total)).isEqualByComparingTo(r.totals().actualExpense()).isEqualByComparingTo("446176.89");
        assertThat(total).allSatisfy(ev -> {
            assertThat(ev.page()).isPositive();
            assertThat(ev.sha256()).hasSize(64);
        });
        assertThat(evidence(g, "grupo:" + UUID.randomUUID())).isEmpty();
    }

    @Test
    void fundFilter() {
        SeptemberGolden g = golden();
        g.confirmMap();
        BudgetScenario c = g.scenario;

        BudgetVsActualResponse condominium = query(g, c.operatingFund.getId());
        BudgetVsActualResponse reserve = query(g, c.reserveFund.getId());

        assertThat(condominium.totals().actualExpense()).isEqualByComparingTo("446176.89");
        assertThat(condominium.groups()).isNotEmpty();
        assertThat(condominium.funds()).isEmpty();
        assertThat(condominium.provisional()).isTrue();
        assertThat(reserve.totals()).isNull();
        assertThat(reserve.groups()).isEmpty();
        assertThat(reserve.provisional()).isFalse();
        assertThat(reserve.funds()).singleElement().satisfies(f -> {
            assertThat(f.fund()).isEqualTo("FUNDO DE RESERVA");
            assertThat(f.collected()).isEqualByComparingTo("14260.79");
        });
        assertThat(reserve.warnings()).extracting(BudgetVsActualWarningResponse::code).doesNotContain("A_REALOCAR");
        assertThatThrownBy(() -> query(g, UUID.randomUUID())).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Fundo não encontrado");
    }

    @Test
    void exportWithFundFilter() throws Exception {
        SeptemberGolden g = golden();
        g.confirmMap();
        var calculation = g.scenario.budgetVsActual.calculate(g.scenario.condominiumId, SEPTEMBER, null,
                g.scenario.reserveFund.getId());
        var report = BudgetVsActualReport.build("Condomínio Piloto", "FUNDO DE RESERVA", calculation, "admin",
                Instant.parse("2026-10-04T15:30:00Z"));

        String pdf = BudgetVsActualExportGoldenTest.pdfText(new BudgetVsActualPdfReport().generate(report));
        List<String> excel = BudgetVsActualExportGoldenTest.excelTexts(new BudgetVsActualExcelReport().generate(report));

        assertThat(pdf).contains("Fundo FUNDO DE RESERVA", "13.548,60 14.260,79 712,19 105,3%")
                .doesNotContain("446.176,89").doesNotContain("PROVISÓRIO");
        assertThat(excel).contains("FUNDO DE RESERVA").doesNotContain("PROVISÓRIO", "Totais do fundo Condomínio");
    }

    @Test
    void adminFixesFundLinkWithTrail() {
        SeptemberGolden g = golden();
        g.confirmMap();
        BudgetScenario c = g.scenario;
        UUID l191 = g.line("1.9.1").getId();
        UUID l192 = g.line("1.9.2").getId();

        // RF-03.1.9: 1.9.2 linked to the "OBRAS" fund by mistake
        c.published.clear();
        c.fundLinkService.change(c.condominiumId, g.budget.getId(), new BudgetFundsRequest(List.of(
                new FundLinkRequest(l191, c.reserveFund.getId()), new FundLinkRequest(l192, c.worksFund.getId()))),
                        "admin");
        BudgetVsActualResponse wrong = query(g, null);

        FundResultResponse works = fund(wrong, "OBRAS");
        assertThat(works.status()).isEqualTo(FundComparisonStatus.COMPARADO);
        assertThat(List.of(works.collected(), works.difference(), works.execution()))
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("25.13"), new BigDecimal("-9007.27"), new BigDecimal("0.3"));
        assertThat(fund(wrong, "OBRAS / REFORMAS / INFRA").status()).isEqualTo(FundComparisonStatus.SEM_PREVISTO_NA_PO);
        assertThat(c.published).filteredOn(BudgetChanged.class::isInstance).hasSize(1);

        // The Admin fixes it: the numbers come back, and the trail records the previous and the new, with who
        c.fundLinkService.change(c.condominiumId, g.budget.getId(), new BudgetFundsRequest(List.of(
                new FundLinkRequest(l191, c.reserveFund.getId()), new FundLinkRequest(l192,
                        c.infraWorksFund.getId()))), "admin");
        BudgetVsActualResponse correct = query(g, null);

        assertThat(fund(correct, "OBRAS / REFORMAS / INFRA").collected()).isEqualByComparingTo("9705.06");
        assertThat(fund(correct, "OBRAS").status()).isEqualTo(FundComparisonStatus.SEM_PREVISTO_NA_PO);
        var changes = c.budgetEvents.stream().filter(e -> e.getType().equals(BudgetEvent.FUNDS_CHANGED)).toList();
        assertThat(changes).hasSize(2).allMatch(e -> e.getUsername().equals("admin"));
        assertThat(changes.get(0).getDetail()).contains("1.9.2", "OBRAS / REFORMAS / INFRA → OBRAS");
        assertThat(changes.get(1).getDetail()).contains("1.9.2", "OBRAS → OBRAS / REFORMAS / INFRA")
                .doesNotContain("1.9.1");

        // No change: no new event
        c.fundLinkService.change(c.condominiumId, g.budget.getId(), new BudgetFundsRequest(List.of(
                new FundLinkRequest(l191, c.reserveFund.getId()), new FundLinkRequest(l192,
                        c.infraWorksFund.getId()))), "admin");
        assertThat(c.budgetEvents).filteredOn(e -> e.getType().equals(BudgetEvent.FUNDS_CHANGED)).hasSize(2);

        // Unlinking a line: "linha 1.9.2 sem fundo ligado"
        c.fundLinkService.change(c.condominiumId, g.budget.getId(), new BudgetFundsRequest(List.of(
                new FundLinkRequest(l191, c.reserveFund.getId()))), "admin");
        assertThat(query(g, null).funds()).anyMatch(f -> "1.9.2".equals(f.lineCode())
                && f.status() == FundComparisonStatus.LINHA_SEM_FUNDO);
    }

    @Test
    void invalidLinkIsRejectedWithoutChangingAnything() {
        SeptemberGolden g = golden();
        BudgetScenario c = g.scenario;
        UUID l191 = g.line("1.9.1").getId();
        UUID l192 = g.line("1.9.2").getId();
        int before = c.fundLinks.size();

        assertThatThrownBy(() -> c.fundLinkService.change(c.condominiumId, g.budget.getId(),
                new BudgetFundsRequest(List.of(
                new FundLinkRequest(l191, c.worksFund.getId()), new FundLinkRequest(l192, c.worksFund.getId()))),
                        "admin"))
                .isInstanceOf(BudgetConfirmationRejectedException.class);
        assertThatThrownBy(() -> c.fundLinkService.change(c.condominiumId, g.budget.getId(),
                new BudgetFundsRequest(List.of(
                new FundLinkRequest(l191, c.reserveFund.getId()), new FundLinkRequest(l191, c.worksFund.getId()))),
                        "admin"))
                .isInstanceOf(BudgetConfirmationRejectedException.class);
        assertThatThrownBy(() -> c.fundLinkService.change(c.condominiumId, g.budget.getId(),
                new BudgetFundsRequest(List.of(
                new FundLinkRequest(l191, c.operatingFund.getId()))), "admin"))
                .isInstanceOf(BudgetConfirmationRejectedException.class);
        assertThatThrownBy(() -> c.fundLinkService.change(c.condominiumId, g.budget.getId(),
                new BudgetFundsRequest(List.of(
                new FundLinkRequest(g.line("1.3.10").getId(), c.worksFund.getId()))), "admin"))
                .isInstanceOf(BudgetConfirmationRejectedException.class);
        assertThat(c.fundLinks).hasSize(before);
        assertThat(c.budgetEvents).noneMatch(e -> e.getType().equals(BudgetEvent.FUNDS_CHANGED));
    }

    private static BudgetVsActualResponse query(SeptemberGolden g, UUID fund) {
        return g.scenario.budgetVsActual.get(g.scenario.condominiumId, SEPTEMBER, null, fund);
    }

    private static List<EvidenceResponse> evidence(SeptemberGolden g, String target) {
        return g.scenario.budgetVsActual.evidence(g.scenario.condominiumId, SEPTEMBER, null, target);
    }

    private static BigDecimal sum(List<EvidenceResponse> list) {
        return list.stream().map(EvidenceResponse::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static FundResultResponse fund(BudgetVsActualResponse r, String name) {
        return r.funds().stream().filter(f -> name.equals(f.fund())).findFirst().orElseThrow();
    }

    private static SeptemberGolden golden() {
        Optional<SeptemberGolden> g = SeptemberGolden.load();
        assumeTrue(g.isPresent() && SeptemberGolden.map().isPresent(), "golden privado ausente");
        return g.get();
    }
}
