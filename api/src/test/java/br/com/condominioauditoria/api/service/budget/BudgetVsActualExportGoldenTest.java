package br.com.condominioauditoria.api.service.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import br.com.condominioauditoria.api.report.BudgetVsActualExcelReport;
import br.com.condominioauditoria.api.report.BudgetVsActualPdfReport;
import br.com.condominioauditoria.api.report.BudgetVsActualReport;
import br.com.condominioauditoria.api.service.audit.ConductTerms;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator;
import br.com.condominioauditoria.api.util.MoneyFormatter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * RF-03.1.14 with September/2026 of the pilot (private golden): the same result becomes JSON (what the API returns),
 * PDF and Excel, and the PDF and Excel numbers equal the JSON ones, cent by cent; "PROVISÓRIO" with the list of the
 * purchases to reallocate; no conduct term (RF-04.15); no chart. Skipped without data/golden/privado.
 */
class BudgetVsActualExportGoldenTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Instant GENERATED_AT = Instant.parse("2026-10-04T15:30:00Z");

    @Test
    void pdfNumbersEqualJson() throws IOException {
        SeptemberGolden g = golden();
        g.confirmMap();
        var calculation = g.scenario.budgetVsActual.calculate(g.scenario.condominiumId, "2026-09", null);
        JsonNode json = JSON.readTree(JSON.writeValueAsString(calculation.result()));
        BudgetVsActualReport report = report(calculation);

        String text = pdfText(new BudgetVsActualPdfReport().generate(report));

        // RF-03.1.14 criterion
        assertThat(text).contains("446.176,89", "451.620,13", "98,8%", "38.880,19", "8,6%");
        JsonNode totals = json.get("totais");
        assertThat(text).contains(seq(totals, "previsto", "despesaRealizada", "emLinhas", "diferenca")
                + " " + percent(totals.get("execucao")));
        JsonNode rule = json.get("regra20");
        assertThat(text).contains(money(rule.get("excesso")) + " " + percent(rule.get("percentual")) + " "
                + money(rule.get("limite")));
        int lines = 0;
        for (JsonNode group : json.get("grupos")) {
            assertThat(text).as("grupo %s", group.get("codigo").asString())
                    .contains(seq(group, "previsto", "realizado", "diferenca") + " " + percent(group.get("execucao")));
            for (JsonNode l : group.get("linhas")) {
                assertThat(text).as("linha %s", l.get("codigo").asString())
                        .contains(seq(l, "previsto", "realizado", "diferenca") + " " + percent(l.get("execucao")));
                lines++;
            }
        }
        assertThat(lines).isGreaterThanOrEqualTo(70);
        for (JsonNode f : json.get("fundos")) {
            if ("COMPARED".equals(f.get("situacao").asString())) {
                assertThat(text).contains(seq(f, "previsto", "arrecadado",
                        "diferenca") + " " + percent(f.get("execucao")));
            }
        }
        // Header
        assertThat(text).contains(g.scenario.files.get(g.budget.getFileId()).getOriginalName(), "versão 1",
                "exercício 05/2026 a 04/2027",
                g.budget.getSha256(), "Período 09/2026", "04/10/2026 12:30", "Admin Teste (admin)",
                "73 de 73 contas confirmadas");
    }

    @Test
    void excelNumbersEqualJsonWithEvidence() throws IOException {
        SeptemberGolden g = golden();
        g.confirmMap();
        var calculation = g.scenario.budgetVsActual.calculate(g.scenario.condominiumId, "2026-09", null);
        JsonNode json = JSON.readTree(JSON.writeValueAsString(calculation.result()));

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(new BudgetVsActualExcelReport().generate(report(calculation))))) {
            Sheet summary = wb.getSheet(BudgetVsActualExcelReport.SUMMARY_SHEET);
            Map<String, Row> lines = new HashMap<>();
            Map<String, Row> groups = new HashMap<>();
            for (Row r : summary) {
                String type = text(r.getCell(0));
                if ("Linha".equals(type)) {
                    lines.put(text(r.getCell(1)), r);
                } else if ("Grupo".equals(type)) {
                    groups.put(text(r.getCell(1)), r);
                }
            }
            int checked = 0;
            for (JsonNode group : json.get("grupos")) {
                Row gr = groups.get(group.get("codigo").asString());
                assertThat(gr).isNotNull();
                assertEqual(gr, 4, group.get("previsto"));
                assertEqual(gr, 5, group.get("realizado"));
                assertEqual(gr, 6, group.get("diferenca"));
                for (JsonNode l : group.get("linhas")) {
                    Row lineItem = lines.get(l.get("codigo").asString());
                    assertThat(lineItem).as("linha %s no Excel", l.get("codigo").asString()).isNotNull();
                    assertEqual(lineItem, 4, l.get("previsto"));
                    assertEqual(lineItem, 5, l.get("realizado"));
                    assertEqual(lineItem, 6, l.get("diferenca"));
                    assertEqual(lineItem, 7, l.get("execucao"));
                    // Money as a masked number, never a formula
                    assertThat(lineItem.getCell(5).getCellType()).isEqualTo(CellType.NUMERIC);
                    assertThat(lineItem.getCell(5).getCellStyle().getDataFormatString()).isEqualTo("#,##0.00");
                    checked++;
                }
            }
            assertThat(checked).isEqualTo(lines.size());
            Row totals = lineBelow(summary, "Totais do fundo Condomínio", 2);
            assertEqual(totals, 0, json.get("totais").get("previsto"));
            assertEqual(totals, 1, json.get("totais").get("despesaRealizada"));
            assertEqual(totals, 4, json.get("totais").get("execucao"));
            assertThat(totals.getCell(1).getNumericCellValue()).isEqualTo(446176.89);
            Row rule = lineBelow(summary, "Regra dos 20% (Conv. 16.2)", 2);
            assertEqual(rule, 0, json.get("regra20").get("excesso"));
            assertEqual(rule, 1, json.get("regra20").get("percentual"));
            assertThat(rule.getCell(0).getNumericCellValue()).isEqualTo(38880.19);
            assertThat(rule.getCell(1).getNumericCellValue()).isEqualTo(8.6);

            // Evidence sheet: one entry per row, with file, page and hash; sum equal to actual
            Sheet evidence = wb.getSheet(BudgetVsActualExcelReport.EVIDENCE_SHEET);
            // Every number compared: lines, separate blocks and collection of the funds linked to the 1.9 lines
            // (credits of funds without planned in the budget make up no number and are left out)
            java.util.Set<String> linked = new java.util.HashSet<>();
            calculation.result().funds().stream().filter(f -> f.lineCode() != null && f.fundId() != null)
                    .forEach(f -> linked.add(BudgetVsActualCalculator.fundTarget(f.fundId())));
            int total = calculation.evidence().entrySet().stream()
                    .filter(x -> !x.getKey().startsWith("fund:") || linked.contains(x.getKey()))
                    .mapToInt(x -> x.getValue().size()).sum();
            assertThat(evidence.getLastRowNum()).isEqualTo(total);
            BigDecimal concierge = BigDecimal.ZERO;
            for (Row r : evidence) {
                if (r.getRowNum() == 0) {
                    continue;
                }
                assertThat(text(r.getCell(9))).isEqualTo(g.file.getOriginalName());
                assertThat(r.getCell(10).getNumericCellValue()).isPositive();
                assertThat(text(r.getCell(12))).hasSize(64);
                if (text(r.getCell(0)).startsWith("1.3.10 ")) {
                    concierge = concierge.add(BigDecimal.valueOf(r.getCell(7).getNumericCellValue()));
                }
            }
            assertThat(concierge).isEqualByComparingTo("86816.34");
            // No chart
            assertThat(((XSSFSheet) summary).getDrawingPatriarch()).isNull();
            assertThat(((XSSFSheet) evidence).getDrawingPatriarch()).isNull();
        }
    }

    @Test
    void provisionalWithListOfPurchasesToReallocate() throws IOException {
        SeptemberGolden g = golden();
        g.confirmMap();
        var calculation = g.scenario.budgetVsActual.calculate(g.scenario.condominiumId, "2026-09", null);
        BudgetVsActualReport report = report(calculation);
        var purchases = calculation.evidence().get(BudgetVsActualCalculator.TARGET_TO_REALLOCATE);
        assertThat(purchases).isNotEmpty();

        String pdf = pdfText(new BudgetVsActualPdfReport().generate(report));
        List<String> excel = excelTexts(new BudgetVsActualExcelReport().generate(report));

        assertThat(report.provisional()).isTrue();
        assertThat(pdf).contains("PROVISÓRIO", "Há valores fora das linhas da PO");
        assertThat(excel).contains("PROVISÓRIO");
        assertThat(report.pendingItems()).hasSize(purchases.size()).allMatch(p -> p.block().equals("A realocar"));
        BigDecimal sum = BigDecimal.ZERO;
        for (var c : purchases) {
            assertThat(pdf).contains("A realocar " + BudgetVsActualReport.date(c.date()) + " " + c.account() + " ");
            assertThat(pdf).contains(MoneyFormatter.format(c.amount()));
            sum = sum.add(c.amount());
        }
        assertThat(sum).isEqualByComparingTo("1050.93");
        assertThat(excel.stream().filter(t -> t.equals("A realocar")).count()).isGreaterThanOrEqualTo(purchases.size());
    }

    @Test
    void noConductTermAndNoChart() throws IOException {
        SeptemberGolden g = golden();
        g.confirmMap();
        for (String period : List.of("2026-09", "acumulado")) {
            var calculation = g.scenario.budgetVsActual.calculate(g.scenario.condominiumId, period, null);
            BudgetVsActualReport report = report(calculation);
            byte[] pdf = new BudgetVsActualPdfReport().generate(report);

            assertThat(ConductTerms.find(new BudgetVsActualPdfReport().html(report))).as("HTML %s", period).isEmpty();
            assertThat(ConductTerms.find(pdfText(pdf))).as("PDF %s", period).isEmpty();
            assertThat(excelTexts(new BudgetVsActualExcelReport().generate(report)).stream().flatMap(t -> ConductTerms
                    .find(t).stream())).as("Excel %s", period).isEmpty();
            try (PDDocument doc = Loader.loadPDF(pdf)) {
                for (PDPage p : doc.getPages()) {
                    for (var name : p.getResources().getXObjectNames()) {
                        assertThat(p.getResources().isImageXObject(name)).as("imagem no PDF").isFalse();
                    }
                }
            }
        }
    }

    @Test
    void twoCashFlowsInMonthExportWarningInsteadOfNumbers() throws IOException {
        SeptemberGolden g = golden();
        g.confirmMap();
        g.scenario.cashFlow("fluxo-corrigido-2026-09.pdf", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 0);
        var calculation = g.scenario.budgetVsActual.calculate(g.scenario.condominiumId, "2026-09", null);
        BudgetVsActualReport report = report(calculation);

        String pdf = pdfText(new BudgetVsActualPdfReport().generate(report));
        List<String> excel = excelTexts(new BudgetVsActualExcelReport().generate(report));

        assertThat(pdf).contains("Dois fluxos para 09/2026: substitua, reclassifique ou exclua um")
                .doesNotContain("446.176,89").doesNotContain("PROVISÓRIO");
        assertThat(excel).contains("Dois fluxos para 09/2026: substitua, reclassifique ou exclua um");
    }

    private static BudgetVsActualReport report(BudgetVsActualCalculator.Calculation calculation) {
        return BudgetVsActualReport.build("Condomínio Piloto", null, calculation, "Admin Teste (admin)", GENERATED_AT);
    }

    static String pdfText(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFTextStripper s = new PDFTextStripper();
            s.setSortByPosition(true);
            return s.getText(doc).replaceAll("[ \\t\\u00a0]+", " ");
        }
    }

    static List<String> excelTexts(byte[] xlsx) throws IOException {
        List<String> texts = new ArrayList<>();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            for (Sheet s : wb) {
                texts.add(s.getSheetName());
                for (Row r : s) {
                    for (Cell c : r) {
                        if (c.getCellType() == CellType.STRING) {
                            texts.add(c.getStringCellValue());
                        }
                    }
                }
            }
        }
        return texts;
    }

    private static Row lineBelow(Sheet sheet, String title, int below) {
        for (Row r : sheet) {
            if (title.equals(text(r.getCell(0)))) {
                return sheet.getRow(r.getRowNum() + below);
            }
        }
        throw new AssertionError("seção ausente: " + title);
    }

    private static void assertEqual(Row r, int col, JsonNode expected) {
        if (expected == null || expected.isNull()) {
            assertThat(text(r.getCell(col))).as("coluna %d da linha %d sem número", col, r.getRowNum()).isEqualTo("—");
            return;
        }
        BigDecimal e = expected.decimalValue();
        BigDecimal read = BigDecimal.valueOf(r.getCell(col).getNumericCellValue()).setScale(e.scale(),
                RoundingMode.HALF_UP);
        assertThat(read).as("coluna %d da linha %d", col, r.getRowNum()).isEqualByComparingTo(e);
    }

    private static String text(Cell c) {
        return c == null || c.getCellType() != CellType.STRING ? "" : c.getStringCellValue();
    }

    private static String seq(JsonNode n, String... fields) {
        List<String> parts = new ArrayList<>();
        for (String c : fields) {
            parts.add(money(n.get(c)));
        }
        return String.join(" ", parts);
    }

    private static String money(JsonNode n) {
        return n == null || n.isNull() ? "—" : MoneyFormatter.format(n.decimalValue());
    }

    private static String percent(JsonNode n) {
        return n == null || n.isNull() ? "—" : BudgetVsActualReport.percentage(n.decimalValue());
    }

    private static SeptemberGolden golden() {
        Optional<SeptemberGolden> g = SeptemberGolden.load();
        assumeTrue(g.isPresent() && SeptemberGolden.map().isPresent(), "golden privado ausente");
        return g.get();
    }
}
