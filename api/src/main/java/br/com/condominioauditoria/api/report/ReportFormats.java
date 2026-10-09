package br.com.condominioauditoria.api.report;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Masks used in the PDF template (the same as Excel and the screen): only formats, never calculates. */
public final class ReportFormats {

    public String money(BigDecimal v) {
        return BudgetVsActualReport.money(v);
    }

    public String percentage(BigDecimal v) {
        return BudgetVsActualReport.percentage(v);
    }

    public String date(LocalDate d) {
        return BudgetVsActualReport.date(d);
    }

    public String month(String yyyyMm) {
        return BudgetVsActualReport.month(yyyyMm);
    }

    public String accounts(java.util.List<String> accounts) {
        return accounts == null || accounts.isEmpty() ? "" : String.join(" ", accounts);
    }

    public String yesNo(Boolean v) {
        return v == null ? "—" : v ? "sim" : "não";
    }
}
