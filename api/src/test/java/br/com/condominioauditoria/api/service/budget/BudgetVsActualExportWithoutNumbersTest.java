package br.com.condominioauditoria.api.service.budget;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.api.report.BudgetVsActualExcelReport;
import br.com.condominioauditoria.api.report.BudgetVsActualPdfReport;
import br.com.condominioauditoria.api.report.BudgetVsActualReport;
import br.com.condominioauditoria.api.service.audit.ConductTerms;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * RF-03.1.14 without golden: a month without a budget exports the message in place of the numbers, without
 * "PROVISÓRIO".
 */
class BudgetVsActualExportWithoutNumbersTest {

    @Test
    void monthWithoutBudgetExportsMessage() throws Exception {
        var calculation = BudgetVsActualCalculator.calculate(new BudgetVsActualCalculator.Input(null, null, null, null,
                null, null, UUID.randomUUID(), null, null, null, null, null,
                new BudgetVsActualCalculator.Month(YearMonth.of(2026, 4))));
        var report = BudgetVsActualReport.build("Condomínio Piloto", null, calculation, "usuario",
                Instant.parse("2026-10-04T15:30:00Z"));

        String pdf = BudgetVsActualExportGoldenTest.pdfText(new BudgetVsActualPdfReport().generate(report));
        var excel = BudgetVsActualExportGoldenTest.excelTexts(new BudgetVsActualExcelReport().generate(report));

        assertThat(pdf).contains("Sem PO aprovada para 04/2026", "Período 04/2026", "Gerado por usuario")
                .doesNotContain("PROVISÓRIO");
        assertThat(excel).contains("Sem PO aprovada para 04/2026", "Resumo", "Evidência").doesNotContain("PROVISÓRIO");
        assertThat(ConductTerms.find(pdf)).isEmpty();
    }
}
