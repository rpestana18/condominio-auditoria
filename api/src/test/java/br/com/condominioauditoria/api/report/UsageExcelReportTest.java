package br.com.condominioauditoria.api.report;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.api.model.enums.UsageFunction;
import br.com.condominioauditoria.api.model.feature.ActivePeriod;
import br.com.condominioauditoria.api.model.usage.UsageTotal;
import br.com.condominioauditoria.api.service.calculator.UsageCostCalculator;
import br.com.condominioauditoria.api.service.feature.FeatureService;
import br.com.condominioauditoria.api.service.usage.UsageService.UsageSummary;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/** Export of the active periods and of the usage in Excel, with the two sheets of RF-10.6. */
class UsageExcelReportTest {

    private static final int DATA = UsageExcelReport.HEADER_ROW + 1;

    @Test
    void generatesBothSheetsWithBrasiliaDatesAndNumericCounts() throws Exception {
        String assistant = FeatureService.ASSISTANT;
        var usage = new UsageSummary(UUID.randomUUID(), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31),
                List.of(),
                List.of(new UsageTotal("2026-11", assistant, UsageFunction.MCP_CALL, 12, 0, 0, 0, 0),
                        new UsageTotal("2026-12", assistant, UsageFunction.INDEXING, 40, 0, 0, 40, 380)));
        var closed = new ActivePeriod(FeatureService.ASSISTANT, Instant.parse("2026-11-01T13:00:00Z"),
                Instant.parse("2026-12-15T18:30:00Z"), "ana", "=HYPERLINK(\"x\")", "bruno", null);
        var open = new ActivePeriod(FeatureService.ASSISTANT, Instant.parse("2027-01-10T13:00:00Z"), null, "carla",
                null, null, null);

        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(
                UsageExcelReport.generate("Mio Residencial Parque", usage, List.of(closed, open))))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
            Sheet periods = workbook.getSheet("Períodos ativos");
            Sheet monthUsage = workbook.getSheet("Uso por mês");
            assertThat(periods).isNotNull();
            assertThat(monthUsage).isNotNull();

            assertThat(periods.getRow(0).getCell(1).getStringCellValue()).isEqualTo("Mio Residencial Parque");
            assertThat(periods.getRow(1).getCell(1).getStringCellValue()).isEqualTo("01/11/2026 a 31/12/2026");
            assertThat(periods.getRow(UsageExcelReport.HEADER_ROW).getCell(4).getStringCellValue())
                    .isEqualTo("Motivo ao ligar");

            Row first = periods.getRow(DATA);
            assertThat(first.getCell(1).getLocalDateTimeCellValue()).isEqualTo(LocalDateTime.of(2026, 11, 1, 10, 0));
            assertThat(first.getCell(2).getLocalDateTimeCellValue()).isEqualTo(LocalDateTime.of(2026, 12, 15, 15, 30));
            assertThat(first.getCell(3).getStringCellValue()).isEqualTo("ana");
            assertThat(first.getCell(4).getCellType()).isEqualTo(CellType.STRING); // text, never a formula
            assertThat(first.getCell(4).getStringCellValue()).isEqualTo("=HYPERLINK(\"x\")");
            assertThat(first.getCell(5).getStringCellValue()).isEqualTo("bruno");
            assertThat(first.getCell(6)).isNull(); // reason not given
            assertThat(periods.getRow(DATA + 1).getCell(2).getStringCellValue()).isEqualTo("ainda ligado");

            Row november = monthUsage.getRow(DATA);
            assertThat(november.getCell(0).getStringCellValue()).isEqualTo("2026-11");
            assertThat(november.getCell(2).getStringCellValue()).isEqualTo("Busca pelo MCP");
            assertThat(november.getCell(3).getNumericCellValue()).isEqualTo(12);
            Row december = monthUsage.getRow(DATA + 1);
            assertThat(december.getCell(6).getNumericCellValue()).isEqualTo(40);
            assertThat(december.getCell(7).getNumericCellValue()).isEqualTo(380);
        }
    }

    @Test
    void withoutPeriodsOrUsageGeneratesHeaderOnlySheets() throws Exception {
        var usage = new UsageSummary(UUID.randomUUID(), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                List.of(), List.of());

        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(UsageExcelReport.generate("C", usage,
                List.of())))) {
            assertThat(workbook.getSheet("Períodos ativos").getLastRowNum()).isEqualTo(UsageExcelReport.HEADER_ROW);
            assertThat(workbook.getSheet("Uso por mês").getLastRowNum()).isEqualTo(UsageExcelReport.HEADER_ROW);
        }
    }

    @Test
    void estimatedCostInDollarsWithTotalAndTextFromBigDecimal() throws Exception {
        String assistant = FeatureService.ASSISTANT;
        var usage = new UsageSummary(UUID.randomUUID(), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                List.of(),
                List.of(new UsageTotal("2026-10", assistant, UsageFunction.DOCUMENT_SEARCH, 5, 0, 0, 0, 0),
                        new UsageTotal("2026-10", assistant, UsageFunction.QUESTION, 30, 1_500_000, 100_000, 0, 0)));
        var cost = new UsageCostCalculator.PeriodCost(java.util.Map.of("2026-10|ASSISTANT|question",
                new java.math.BigDecimal("1234.50")), java.util.Map.of(), new java.math.BigDecimal("1234.50"),
                java.util.Set.of());

        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(
                UsageExcelReport.generate("C", usage, List.of(), cost)))) {
            Sheet sheet = workbook.getSheet("Uso por mês");
            assertThat(sheet.getRow(UsageExcelReport.HEADER_ROW).getCell(8).getStringCellValue())
                    .isEqualTo("Custo estimado (US$)");
            assertThat(sheet.getRow(DATA).getCell(8)).isNull(); // search: no tokens, no cost
            assertThat(sheet.getRow(DATA + 1).getCell(8).getStringCellValue()).isEqualTo("1.234,50");
            Row total = sheet.getRow(DATA + 3);
            assertThat(total.getCell(0).getStringCellValue()).isEqualTo("Total do período");
            assertThat(total.getCell(8).getStringCellValue()).isEqualTo("1.234,50");
        }
    }

    @Test
    void withoutCatalogWarnsThatTheCostIsUnavailable() throws Exception {
        var usage = new UsageSummary(UUID.randomUUID(), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                List.of(),
                List.of(new UsageTotal("2026-10", FeatureService.ASSISTANT, UsageFunction.QUESTION, 1, 10, 10, 0, 0)));

        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(
                UsageExcelReport.generate("C", usage, List.of(), null)))) {
            Sheet sheet = workbook.getSheet("Uso por mês");
            assertThat(sheet.getRow(DATA + 2).getCell(0).getStringCellValue()).contains("indisponível");
        }
    }
}
