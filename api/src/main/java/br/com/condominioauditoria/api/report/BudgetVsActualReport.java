package br.com.condominioauditoria.api.report;

import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualGroupResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualLineResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualResponse;
import br.com.condominioauditoria.api.dto.response.budget.EvidenceResponse;
import br.com.condominioauditoria.api.dto.response.budget.FundResultResponse;
import br.com.condominioauditoria.api.model.enums.BudgetVsActualStatus;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.Calculation;
import br.com.condominioauditoria.api.util.MoneyFormatter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * What the PDF and Excel export shows (RF-03.1.14), built from the <b>same</b> {@link Calculation} as the screen: no
 * number is recalculated here, only formatted. Header (condominium, budget with file, version, hash and fiscal year,
 * period, date and time, who generated it, mapping status), the "PROVISÓRIO" mark with the list of what is missing,
 * and the evidence per line. Pure function: the generation instant is a parameter.
 */
public record BudgetVsActualReport(String condominium, String period, String fund, String generatedAt,
        String generatedBy, String mappingStatus, BudgetVsActualResponse result, boolean calculated,
                boolean provisional,
        List<PendingItem> pendingItems, List<EvidenceGroup> evidence) {

    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZONE);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Item that makes the result provisional: an entry to reallocate or without budget line. */
    public record PendingItem(String block, EvidenceResponse entry) {
    }

    /** Entries that make up a number (the Excel "Evidência" sheet). */
    public record EvidenceGroup(String target, String label, List<EvidenceResponse> entries) {
    }

    public static BudgetVsActualReport build(String condominium, String fund, Calculation calculation,
            String generatedBy,
            Instant generatedAt) {
        BudgetVsActualResponse r = calculation.result();
        boolean calculated = r.status() == BudgetVsActualStatus.CALCULATED;
        List<PendingItem> pendingItems = new ArrayList<>();
        if (calculated) {
            calculation.evidence().getOrDefault(BudgetVsActualCalculator.TARGET_TO_REALLOCATE, List.of())
                    .forEach(ev -> pendingItems.add(new PendingItem("A realocar", ev)));
            calculation.evidence().getOrDefault(BudgetVsActualCalculator.TARGET_WITHOUT_BUDGET_LINE, List.of())
                    .forEach(ev -> pendingItems.add(new PendingItem("Sem linha da PO", ev)));
        }
        List<EvidenceGroup> evidence = new ArrayList<>();
        if (calculated) {
            for (BudgetVsActualGroupResponse g : r.groups()) {
                for (BudgetVsActualLineResponse l : g.lines()) {
                    List<EvidenceResponse> ev = calculation.evidence().get(BudgetVsActualCalculator.lineTarget(l.lineId()));
                    if (ev != null && !ev.isEmpty()) {
                        evidence.add(new EvidenceGroup(BudgetVsActualCalculator.lineTarget(l.lineId()),
                                l.code() + " " + l.description(), ev));
                    }
                }
            }
            add(evidence, calculation, BudgetVsActualCalculator.TARGET_ADJUSTMENTS, "Ajustes (não são despesa)");
            add(evidence, calculation, BudgetVsActualCalculator.TARGET_TO_REALLOCATE, "A realocar");
            add(evidence, calculation, BudgetVsActualCalculator.TARGET_WITHOUT_BUDGET_LINE, "Sem linha da PO");
            add(evidence, calculation, BudgetVsActualCalculator.TARGET_TRANSFERS, "Transferências (fora)");
            for (FundResultResponse f : r.funds()) {
                if (f.fundId() != null && f.lineCode() != null) {
                    add(evidence, calculation, BudgetVsActualCalculator.fundTarget(f.fundId()),
                            "Arrecadação " + f.fund() + " (" + f.lineCode() + ")");
                }
            }
        }
        String mapping = r.mapping() == null ? "—" : r.mapping().confirmed() + " de " + r.mapping().accounts()
                + " contas confirmadas" + (r.mapping().withoutConfirmedMapping() == 0 ? ""
                : "; " + r.mapping().withoutConfirmedMapping() + " sem de-para confirmado");
        return new BudgetVsActualReport(condominium, period(r), fund == null ? "Todos" : fund,
                DATE_TIME.format(generatedAt), generatedBy, mapping, r, calculated, calculated && r.provisional(),
                List.copyOf(pendingItems), List.copyOf(evidence));
    }

    private static void add(List<EvidenceGroup> list, Calculation c, String target, String label) {
        List<EvidenceResponse> ev = c.evidence().get(target);
        if (ev != null && !ev.isEmpty()) {
            list.add(new EvidenceGroup(target, label, ev));
        }
    }

    private static String period(BudgetVsActualResponse r) {
        if (!"acumulado".equals(r.period())) {
            return BudgetVsActualCalculator.mmyyyy(YearMonth.parse(r.period()));
        }
        String summed = r.summedMonths().isEmpty() ? "nenhum"
                : BudgetVsActualCalculator.monthList(r.summedMonths());
        return "Acumulado do exercício (meses somados: " + summed + ")";
    }

    /** There are numbers of the Condomínio fund (false when the filter is another fund). */
    public boolean withOperatingFund() {
        return calculated && result.totals() != null;
    }

    /** There is a funds panel (false when the filter is only the Condomínio fund). */
    public boolean withFunds() {
        return calculated && !result.funds().isEmpty();
    }

    /** Budget text in the header: file, version, hash and fiscal year. */
    public String budget() {
        var p = result.budget();
        if (p == null) {
            return "—";
        }
        return Objects.requireNonNullElse(p.fileName(), "arquivo " + p.fileId()) + "; versão "
                + (p.version() == null ? "—" : p.version()) + "; exercício " + month(p.fiscalYearStart()) + " a "
                + month(p.fiscalYearEnd()) + "; hash " + p.sha256();
    }

    /** Document title, no chart (the user's decision, ADR 0004, question 7). */
    public String title() {
        return "Previsto × realizado – " + condominium;
    }

    // Formatting: the same numbers as the JSON, only with the Brazilian mask

    public static String money(BigDecimal v) {
        return v == null ? "—" : MoneyFormatter.format(v);
    }

    public static String percentage(BigDecimal v) {
        return v == null ? "—" : v.toPlainString().replace('.', ',') + "%";
    }

    public static String date(LocalDate d) {
        return d == null ? "—" : DATE.format(d);
    }

    public static String month(String yyyyMm) {
        return yyyyMm == null ? "—" : BudgetVsActualCalculator.mmyyyy(YearMonth.parse(yyyyMm));
    }
}
