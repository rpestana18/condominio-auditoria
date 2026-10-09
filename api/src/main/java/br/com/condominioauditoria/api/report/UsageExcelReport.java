package br.com.condominioauditoria.api.report;

import br.com.condominioauditoria.api.model.feature.ActivePeriod;
import br.com.condominioauditoria.api.model.usage.UsageTotal;
import br.com.condominioauditoria.api.service.calculator.UsageCostCalculator;
import br.com.condominioauditoria.api.service.usage.UsageService;
import br.com.condominioauditoria.api.service.usage.UsageService.UsageSummary;
import br.com.condominioauditoria.api.util.MoneyFormatter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Active periods and usage of the period in Excel (.xlsx), RF-10.6 and RF-09.7, with Apache POI (docs/tecnologias.md,
 * T11). Two sheets: "Períodos ativos" and "Uso por mês". Dates and times in date cells, in Brasília time; counts in
 * numeric cells. Free text (e.g. reason) goes as text, never as a formula. No billing amounts in this phase.
 *
 * Estimated cost in US$ (RF-09.7; ADR 0003, Decision 4): a column per month and function and a total row, calculated by
 * {@link UsageCostCalculator} (tokens × price of the rag's catalog, 2 decimals only on totals). Empty cell = no tokens;
 * "sem preço" = tokens of a model outside the catalog. The amount goes as text in the Brazilian format (1.234,56),
 * formatted from the BigDecimal, never as a floating point number. Without the catalog (rag down), the spreadsheet
 * comes out without cost and with a warning.
 */
public final class UsageExcelReport {

    public static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    static final String PERIODS_SHEET = "Períodos ativos";
    static final String USAGE_SHEET = "Uso por mês";
    /** Row (0-based) of the table header: condominium, period and a blank row come before it. */
    static final int HEADER_ROW = 3;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private UsageExcelReport() {
    }

    static final String COST_COLUMN = "Custo estimado (US$)";

    public static byte[] generate(String condominiumName, UsageSummary usage, List<ActivePeriod> periods) {
        return generate(condominiumName, usage, periods, null);
    }

    /** Null cost = price catalog unavailable. */
    public static byte[] generate(String condominiumName, UsageSummary usage, List<ActivePeriod> periods,
            UsageCostCalculator.PeriodCost cost) {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            String period = DATE.format(usage.start()) + " a " + DATE.format(usage.end());

            Sheet periodsSheet = sheet(workbook, PERIODS_SHEET, condominiumName, period, styles, List.of("Módulo",
                    "Início", "Fim", "Ligado por", "Motivo ao ligar", "Desligado por", "Motivo ao desligar"),
                    new int[] {14, 20, 20, 22, 40, 22, 40});
            int n = HEADER_ROW + 1;
            for (ActivePeriod p : periods) {
                Row row = periodsSheet.createRow(n++);
                text(row, 0, p.feature());
                dateTimeCell(row, 1, p.start(), styles);
                if (p.end() == null) {
                    text(row, 2, "ainda ligado");
                } else {
                    dateTimeCell(row, 2, p.end(), styles);
                }
                text(row, 3, p.enabledBy());
                text(row, 4, p.enableReason());
                text(row, 5, p.disabledBy());
                text(row, 6, p.disableReason());
            }

            Sheet usageSheet = sheet(workbook, USAGE_SHEET, condominiumName, period, styles, List.of("Mês", "Módulo",
                    "Função", "Quantidade", "Tokens de entrada", "Tokens de saída", "Arquivos", "Páginas", COST_COLUMN),
                    new int[] {10, 14, 18, 12, 18, 16, 10, 10, 20});
            n = HEADER_ROW + 1;
            for (UsageTotal t : usage.byMonth()) {
                Row row = usageSheet.createRow(n++);
                text(row, 0, t.month());
                text(row, 1, t.feature());
                text(row, 2, t.function().code());
                row.createCell(3).setCellValue(t.count());
                row.createCell(4).setCellValue(t.inputTokens());
                row.createCell(5).setCellValue(t.outputTokens());
                row.createCell(6).setCellValue(t.files());
                row.createCell(7).setCellValue(t.pages());
                if (cost != null && (t.inputTokens() > 0 || t.outputTokens() > 0)) {
                    costCell(row, 8, cost.ofMonth(t), styles);
                }
            }
            boolean hasTokens = usage.byMonth().stream().anyMatch(t -> t.inputTokens() > 0 || t.outputTokens() > 0);
            if (cost != null || hasTokens) {
                n++;
                if (cost == null) {
                    text(usageSheet.createRow(n), 0, "Custo estimado indisponível: o catálogo de preços do serviço rag"
                            + " não respondeu.");
                } else {
                    Row total = usageSheet.createRow(n++);
                    cell(total, 0, "Total do período", styles.bold);
                    costCell(total, 8, cost.total(), styles);
                    text(usageSheet.createRow(n++), 0, "Custo estimado em US$: tokens × preço por milhão de tokens do"
                            + " catálogo de IA, arredondado a 2 casas só nos totais. Não é valor de cobrança.");
                    if (!cost.modelsWithoutPrice().isEmpty()) {
                        text(usageSheet.createRow(n), 0, "Sem preço no catálogo (custo não estimado): "
                                + String.join(", ", cost.modelsWithoutPrice()));
                    }
                }
            }

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException error) {
            throw new UncheckedIOException("Falha ao gerar o Excel de uso", error);
        }
    }

    private static Sheet sheet(XSSFWorkbook workbook, String name, String condominium, String period, Styles styles,
            List<String> columns, int[] widths) {
        Sheet sheet = workbook.createSheet(name);
        Row r0 = sheet.createRow(0);
        cell(r0, 0, "Condomínio", styles.bold);
        text(r0, 1, condominium);
        Row r1 = sheet.createRow(1);
        cell(r1, 0, "Período", styles.bold);
        text(r1, 1, period);
        Row header = sheet.createRow(HEADER_ROW);
        for (int i = 0; i < columns.size(); i++) {
            cell(header, i, columns.get(i), styles.bold);
            sheet.setColumnWidth(i, widths[i] * 256);
        }
        sheet.createFreezePane(0, HEADER_ROW + 1);
        return sheet;
    }

    private static void text(Row row, int column, String value) {
        if (value != null) {
            row.createCell(column).setCellValue(value);
        }
    }

    private static void cell(Row row, int column, String value, CellStyle style) {
        Cell c = row.createCell(column);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    private static void costCell(Row row, int column, BigDecimal value, Styles styles) {
        if (value == null) {
            text(row, column, "sem preço");
            return;
        }
        // Text formatted from the BigDecimal (1.234,56), never through floating point
        Cell c = row.createCell(column);
        c.setCellValue(MoneyFormatter.format(value));
        c.setCellStyle(styles.right);
    }

    private static void dateTimeCell(Row row, int column, Instant instant, Styles styles) {
        if (instant == null) {
            return;
        }
        Cell c = row.createCell(column);
        c.setCellValue(LocalDateTime.ofInstant(instant, UsageService.ZONE));
        c.setCellStyle(styles.dateTime);
    }

    private static final class Styles {
        final CellStyle bold;
        final CellStyle dateTime;
        final CellStyle right;

        Styles(XSSFWorkbook workbook) {
            Font font = workbook.createFont();
            font.setBold(true);
            bold = workbook.createCellStyle();
            bold.setFont(font);
            dateTime = workbook.createCellStyle();
            dateTime.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy hh:mm:ss"));
            right = workbook.createCellStyle();
            right.setAlignment(HorizontalAlignment.RIGHT);
        }
    }
}
