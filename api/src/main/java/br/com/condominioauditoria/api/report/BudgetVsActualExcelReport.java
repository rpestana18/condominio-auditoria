package br.com.condominioauditoria.api.report;

import br.com.condominioauditoria.api.dto.response.budget.BlockAccountResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualGroupResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualLineResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualResponse;
import br.com.condominioauditoria.api.dto.response.budget.EntryBlockResponse;
import br.com.condominioauditoria.api.dto.response.budget.EvidenceResponse;
import br.com.condominioauditoria.api.dto.response.budget.FiscalYearMonthResponse;
import br.com.condominioauditoria.api.dto.response.budget.FundResultResponse;
import br.com.condominioauditoria.api.report.BudgetVsActualReport.EvidenceGroup;
import br.com.condominioauditoria.api.report.BudgetVsActualReport.PendingItem;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * Excel (.xlsx) of budget vs. actual (RF-03.1.14; ADR 0004, Decision 6), with Apache POI. "Resumo" sheet (header,
 * "PROVISÓRIO" with the list of what is missing, totals, the 20% rule, groups and lines, blocks, check, funds, months,
 * warnings) and "Evidência" sheet (one entry per row, with file, page and hash). Money is written as a number from the
 * result's BigDecimal, with a thousands mask and 2 decimals, and never as a formula: the file shows the same cents as
 * the screen. No chart.
 */
@Component
public class BudgetVsActualExcelReport {

    public static final String SUMMARY_SHEET = "Resumo";
    public static final String EVIDENCE_SHEET = "Evidência";

    public byte[] generate(BudgetVsActualReport report) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Styles e = new Styles(wb);
            summary(wb.createSheet(SUMMARY_SHEET), report, e);
            evidence(wb.createSheet(EVIDENCE_SHEET), report, e);
            wb.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Falha ao gerar o Excel do previsto × realizado", ex);
        }
    }

    private static final class Styles {
        final CellStyle money;
        final CellStyle percentage;
        final CellStyle title;
        final CellStyle header;
        final CellStyle group;
        final CellStyle groupMoney;
        final CellStyle groupPercentage;
        final CellStyle alert;

        public Styles(XSSFWorkbook wb) {
            short moneyFormat = wb.createDataFormat().getFormat("#,##0.00");
            short percentFormat = wb.createDataFormat().getFormat("0.0\"%\"");
            Font bold = wb.createFont();
            bold.setBold(true);
            Font large = wb.createFont();
            large.setBold(true);
            large.setFontHeightInPoints((short) 13);
            Font red = wb.createFont();
            red.setBold(true);
            red.setColor(IndexedColors.DARK_RED.getIndex());
            red.setFontHeightInPoints((short) 12);
            money = wb.createCellStyle();
            money.setDataFormat(moneyFormat);
            percentage = wb.createCellStyle();
            percentage.setDataFormat(percentFormat);
            title = wb.createCellStyle();
            title.setFont(large);
            header = wb.createCellStyle();
            header.setFont(bold);
            header.setBorderBottom(BorderStyle.THIN);
            group = wb.createCellStyle();
            group.setFont(bold);
            groupMoney = wb.createCellStyle();
            groupMoney.cloneStyleFrom(money);
            groupMoney.setFont(bold);
            groupPercentage = wb.createCellStyle();
            groupPercentage.cloneStyleFrom(percentage);
            groupPercentage.setFont(bold);
            alert = wb.createCellStyle();
            alert.setFont(red);
        }
    }

    /** Writes row by row on a sheet. */
    private static final class Writer {
        final Sheet sheet;
        final Styles styles;
        int row;

        public Writer(Sheet sheet, Styles e) {
            this.sheet = sheet;
            this.styles = e;
        }

        public Row newRow() {
            return sheet.createRow(row++);
        }

        public void skip() {
            row++;
        }

        public Row texts(CellStyle style, String... values) {
            Row r = newRow();
            for (int i = 0; i < values.length; i++) {
                text(r, i, values[i], style);
            }
            return r;
        }

        public void pair(String label, String amount) {
            Row r = newRow();
            text(r, 0, label, styles.header);
            text(r, 1, amount, null);
        }

        public static void text(Row r, int col, String v, CellStyle style) {
            Cell c = r.createCell(col);
            if (v != null) {
                c.setCellValue(v);
            }
            if (style != null) {
                c.setCellStyle(style);
            }
        }

        public static void number(Row r, int col, BigDecimal v, CellStyle style) {
            Cell c = r.createCell(col);
            if (v != null) {
                c.setCellValue(v.doubleValue());
                c.setCellStyle(style);
            } else {
                c.setCellValue("—");
            }
        }

        public static void integer(Row r, int col, int v) {
            r.createCell(col).setCellValue(v);
        }
    }

    private static void summary(Sheet sheet, BudgetVsActualReport report, Styles e) {
        BudgetVsActualResponse r = report.result();
        Writer w = new Writer(sheet, e);
        w.texts(e.title, report.title());
        if (report.provisional()) {
            w.texts(e.alert, "PROVISÓRIO");
        }
        w.pair("Condomínio", report.condominium());
        w.pair("PO", report.budget());
        w.pair("Período", report.period());
        w.pair("Fundo", report.fund());
        w.pair("Gerado em", report.generatedAt());
        w.pair("Gerado por", report.generatedBy());
        w.pair("Estado do de-para", report.mappingStatus());
        w.pair("Versão do cálculo", r.calculationVersion());
        w.skip();

        if (report.provisional()) {
            w.texts(e.alert, "PROVISÓRIO: há valores fora das linhas da PO; os números podem mudar quando estes itens"
                    + " forem tratados");
            w.texts(e.header, "Bloco", "Data", "Conta", "Nome da conta", "Histórico", "Valor", "Arquivo", "Página");
            for (PendingItem p : report.pendingItems()) {
                EvidenceResponse l = p.entry();
                Row row = w.texts(null, p.block(), BudgetVsActualReport.date(l.date()), l.account(),
                        l.accountName(), l.memo());
                Writer.number(row, 5, l.amount(), e.money);
                Writer.text(row, 6, l.fileName(), null);
                Writer.integer(row, 7, l.page());
            }
            w.skip();
        }

        if (!report.calculated()) {
            w.texts(e.header, "Sem números para este período");
            w.texts(null, r.message());
            warnings(w, r);
            widths(sheet);
            return;
        }

        if (report.withOperatingFund()) {
            condominium(w, r);
        }
        if (report.withFunds()) {
            funds(w, r);
        }
        months(w, r);
        warnings(w, r);
        w.skip();
        w.texts(null, "Os números saem do mesmo cálculo da tela e do PDF. As diferenças são fatos a verificar com a"
                + " evidência (aba \"" + EVIDENCE_SHEET + "\"); este arquivo não descreve causas.");
        widths(sheet);
    }

    private static void condominium(Writer w, BudgetVsActualResponse r) {
        Styles e = w.styles;
        w.texts(e.header, "Totais do fundo Condomínio");
        totals(w, r);
        w.skip();

        if (r.rule20() != null) {
            var g = r.rule20();
            w.texts(e.header, "Regra dos 20% (Conv. 16.2)");
            w.texts(e.header, "Excesso", "% do previsto do mês", "Limite", "Linhas acima do previsto", "A realocar",
                    "Sem linha da PO", "Cenário máximo", "% cenário máximo", "Acima do limite", "Provisório");
            Row row = w.newRow();
            Writer.number(row, 0, g.overrun(), e.money);
            Writer.number(row, 1, g.percentage(), e.percentage);
            Writer.number(row, 2, g.limit(), e.money);
            Writer.integer(row, 3, g.linesAbove());
            Writer.number(row, 4, g.toReallocate(), e.money);
            Writer.number(row, 5, g.withoutBudgetLine(), e.money);
            Writer.number(row, 6, g.maxScenario(), e.money);
            Writer.number(row, 7, g.maxScenarioPercentage(), e.percentage);
            Writer.text(row, 8, g.aboveLimit() ? "sim" : "não", null);
            Writer.text(row, 9, g.provisional() ? "sim" : "não", null);
            w.skip();
        }

        w.texts(e.header, "Por grupo e linha da PO");
        w.texts(e.header, "Tipo", "Código", "Descrição", "Contas do fluxo", "Previsto", "Realizado", "Diferença",
                "Execução", "Observações da PO", "Página da PO");
        for (BudgetVsActualGroupResponse g : r.groups()) {
            Row row = w.texts(e.group, "Grupo", g.code(), g.description(), "");
            Writer.number(row, 4, g.planned(), e.groupMoney);
            Writer.number(row, 5, g.actual(), e.groupMoney);
            Writer.number(row, 6, g.difference(), e.groupMoney);
            Writer.number(row, 7, g.execution(), e.groupPercentage);
            for (BudgetVsActualLineResponse l : g.lines()) {
                Row lineItem = w.texts(null, "Linha", l.code(), l.description(), String.join(" ",
                        l.cashFlowAccounts()));
                Writer.number(lineItem, 4, l.planned(), e.money);
                Writer.number(lineItem, 5, l.actual(), e.money);
                Writer.number(lineItem, 6, l.difference(), e.money);
                Writer.number(lineItem, 7, l.execution(), e.percentage);
                Writer.text(lineItem, 8, l.notes(), null);
                Writer.integer(lineItem, 9, l.page());
            }
        }
        w.skip();

        w.texts(e.header, "Blocos à parte (fora das linhas da PO)");
        w.texts(e.header, "Bloco", "Conta", "Nome", "Detalhe", "Lançamentos", "Valor");
        block(w, "Ajustes (não são despesa)", r.adjustments());
        block(w, "A realocar", r.toReallocate());
        block(w, "Sem linha da PO", r.withoutBudgetLine());
        w.skip();

        var c = r.cashFlowCheck();
        w.texts(e.header, "Conferência com o fluxo");
        w.texts(e.header, "Débitos do fundo", "Lançamentos", "Despesa realizada", "Ajustes", "Transferências",
                "Confere");
        Row cr = w.newRow();
        Writer.number(cr, 0, c.fundDebits(), e.money);
        Writer.integer(cr, 1, c.entries());
        Writer.number(cr, 2, c.actualExpense(), e.money);
        Writer.number(cr, 3, c.adjustments(), e.money);
        Writer.number(cr, 4, c.transfers(), e.money);
        Writer.text(cr, 5, c.matches() ? "sim" : "não", null);
        w.skip();

    }

    private static void funds(Writer w, BudgetVsActualResponse r) {
        Styles e = w.styles;
        w.texts(e.header, "Fundos");
        w.texts(e.header, "Fundo", "Linha da PO", "Situação", "Previsto", "Arrecadado", "Diferença", "Execução",
                "Créditos", "Débitos");
        for (FundResultResponse f : r.funds()) {
            Row fr = w.texts(null, f.fund() == null ? "—" : f.fund(), f.lineCode() == null ? "—" : f.lineCode(),
                    switch (f.status()) {
                        case COMPARADO -> "arrecadação × previsto";
                        case SEM_PREVISTO_NA_PO -> "sem previsto na PO";
                        case LINHA_SEM_FUNDO -> "linha sem fundo ligado";
                        case REPROCESSAR_FLUXO -> "reprocesse o fluxo";
                    });
            Writer.number(fr, 3, f.planned(), e.money);
            Writer.number(fr, 4, f.collected(), e.money);
            Writer.number(fr, 5, f.difference(), e.money);
            Writer.number(fr, 6, f.execution(), e.percentage);
            Writer.number(fr, 7, f.credits(), e.money);
            Writer.number(fr, 8, f.debits(), e.money);
        }
        w.skip();

    }

    private static void months(Writer w, BudgetVsActualResponse r) {
        Styles e = w.styles;
        if (r.months().size() > 1) {
            w.texts(e.header, "Meses do exercício");
            w.texts(e.header, "Mês", "Situação", "Previsto", "Despesa realizada", "Excesso", "% excesso");
            for (FiscalYearMonthResponse m : r.months()) {
                Row mr = w.texts(null, BudgetVsActualReport.month(m.month()), switch (m.status()) {
                    case COM_FLUXO -> "com fluxo";
                    case SEM_FLUXO -> "sem fluxo carregado";
                    case DOIS_FLUXOS -> "dois fluxos";
                });
                Writer.number(mr, 2, m.planned(), e.money);
                Writer.number(mr, 3, m.actualExpense(), e.money);
                Writer.number(mr, 4, m.overrun(), e.money);
                Writer.number(mr, 5, m.overrunPercentage(), e.percentage);
            }
            w.skip();
        }
    }

    private static void totals(Writer w, BudgetVsActualResponse r) {
        Styles e = w.styles;
        w.texts(e.header, "Previsto", "Despesa realizada", "Em linhas da PO", "Diferença", "Execução",
                "Previsto do mês", "Previsto do exercício (referência)");
        Row row = w.newRow();
        var t = r.totals();
        Writer.number(row, 0, t.planned(), e.money);
        Writer.number(row, 1, t.actualExpense(), e.money);
        Writer.number(row, 2, t.inLines(), e.money);
        Writer.number(row, 3, t.difference(), e.money);
        Writer.number(row, 4, t.execution(), e.percentage);
        Writer.number(row, 5, t.monthlyPlanned(), e.money);
        Writer.number(row, 6, t.fiscalYearPlanned(), e.money);
    }

    private static void block(Writer w, String name, EntryBlockResponse b) {
        Row row = w.texts(w.styles.group, name, "", "", "");
        Writer.integer(row, 4, b.entries());
        Writer.number(row, 5, b.total(), w.styles.groupMoney);
        for (BlockAccountResponse c : b.accounts()) {
            Row cr = w.texts(null, "", c.account() == null ? "—" : c.account(), c.name(), c.detail());
            Writer.integer(cr, 4, c.entries());
            Writer.number(cr, 5, c.amount(), w.styles.money);
        }
    }

    private static void warnings(Writer w, BudgetVsActualResponse r) {
        if (r.warnings().isEmpty()) {
            return;
        }
        w.texts(w.styles.header, "Avisos");
        r.warnings().forEach(a -> w.texts(null, a.text()));
    }

    private static void evidence(Sheet sheet, BudgetVsActualReport report, Styles e) {
        Writer w = new Writer(sheet, e);
        w.texts(e.header, "Número (linha da PO ou bloco)", "Data", "Conta", "Nome da conta", "Histórico",
                "Fornecedor", "Documento", "Valor", "Fundo", "Arquivo", "Página", "Ordem", "Hash (SHA-256)",
                "Realocação");
        for (EvidenceGroup g : report.evidence()) {
            for (EvidenceResponse l : g.entries()) {
                Row row = w.texts(null, g.label(), BudgetVsActualReport.date(l.date()), l.account(),
                        l.accountName(), l.memo(), l.supplier(), l.document());
                Writer.number(row, 7, l.amount(), e.money);
                Writer.text(row, 8, l.fund(), null);
                Writer.text(row, 9, l.fileName(), null);
                Writer.integer(row, 10, l.page());
                Writer.integer(row, 11, l.position());
                Writer.text(row, 12, l.sha256(), null);
                Writer.text(row, 13, l.reallocation(), null);
            }
        }
        sheet.createFreezePane(0, 1);
        widths(sheet);
    }

    private static void widths(Sheet sheet) {
        int[] width = {28, 12, 34, 22, 40, 16, 16, 14, 30, 30, 10, 8, 20, 30};
        for (int i = 0; i < width.length; i++) {
            sheet.setColumnWidth(i, width[i] * 256);
        }
    }

    /** Used in tests: labels of the exported numbers. */
    public static List<String> sheets() {
        return List.of(SUMMARY_SHEET, EVIDENCE_SHEET);
    }
}
