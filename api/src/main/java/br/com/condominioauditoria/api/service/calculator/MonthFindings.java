package br.com.condominioauditoria.api.service.calculator;

import br.com.condominioauditoria.api.dto.response.budget.BlockAccountResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualLineResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualResponse;
import br.com.condominioauditoria.api.dto.response.budget.EvidenceResponse;
import br.com.condominioauditoria.api.dto.response.budget.UsedCashFlowResponse;
import br.com.condominioauditoria.api.model.enums.BudgetVsActualStatus;
import br.com.condominioauditoria.api.service.audit.FindingSyncService.AssessedFinding;
import br.com.condominioauditoria.api.service.audit.FindingSyncService.Evidence;
import br.com.condominioauditoria.api.service.audit.rule.MonthlyOverrunRule;
import br.com.condominioauditoria.api.service.audit.rule.UnmappedAccountRule;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.Calculation;
import br.com.condominioauditoria.api.util.MoneyFormatter;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Findings of a month from the {@link BudgetVsActualCalculator} result (the same as the screen): the 20% rule
 * (critical, RF-03.1.11) and account without budget line (attention, RF-03.1.6 and RF-02.7). Pure function. The texts
 * describe the fact, the rule and what to check; never the cause (RF-03.1.12).
 *
 * @param rules rules assessed in this month: only their findings can change state (the 20% rule is not assessed
 *     without the registered limit, and then its findings stay as they are)
 */
public record MonthFindings(YearMonth month, Set<String> rules, List<AssessedFinding> assessed) {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    public static final String OPERATING_FUND_TARGET = "operating-fund";

    /** Empty (no rule assessed) when the month has no numbers: no cash flow, two cash flows, no budget etc. */
    public static MonthFindings assess(YearMonth month, Calculation calculation) {
        BudgetVsActualResponse r = calculation.result();
        if (r.status() != BudgetVsActualStatus.CALCULATED) {
            return new MonthFindings(month, Set.of(), List.of());
        }
        Set<String> rules = new LinkedHashSet<>();
        List<AssessedFinding> assessed = new ArrayList<>();
        rules.add(UnmappedAccountRule.CODE);
        accountsWithoutLine(month, calculation, assessed);
        if (r.rule20() != null) {
            rules.add(MonthlyOverrunRule.CODE);
            if (r.rule20().aboveLimit()) {
                assessed.add(overrun(month, r));
            }
        }
        return new MonthFindings(month, Set.copyOf(rules), List.copyOf(assessed));
    }

    private static void accountsWithoutLine(YearMonth month, Calculation calculation, List<AssessedFinding> assessed) {
        BudgetVsActualResponse r = calculation.result();
        List<EvidenceResponse> entries = calculation.evidence()
                .getOrDefault(BudgetVsActualCalculator.TARGET_WITHOUT_BUDGET_LINE, List.of());
        for (BlockAccountResponse c : r.withoutBudgetLine().accounts()) {
            List<Evidence> proofs = entries.stream().filter(ev -> Objects.equals(ev.account(), c.account()))
                    .map(ev -> new Evidence(ev.fileId(), ev.sha256(), ev.page(), "Lançamento de "
                            + DATE.format(ev.date()) + (ev.account() == null ? ", sem conta" : ", conta " + ev.account())
                            + ", R$ " + MoneyFormatter.format(ev.amount()) + " (ordem " + ev.position() + "): "
                            + ev.memo(), null))
                    .toList();
            String account = c.account() == null ? "Lançamentos sem conta do fluxo"
                    : "Conta " + c.account() + (c.name() == null || c.name().isBlank() ? "" : " " + c.name());
            String description = account + " no fundo Condomínio em " + BudgetVsActualCalculator.mmyyyy(month)
                    + ": R$ " + MoneyFormatter.format(c.amount()) + " em " + c.entries()
                    + (c.entries() == 1 ? " lançamento" : " lançamentos") + " sem linha da PO ("
                    + (c.detail() == null ? "sem de-para" : c.detail()) + "). Os valores entram na despesa"
                    + " realizada e em nenhuma linha da PO; verificar o de-para da conta.";
            assessed.add(new AssessedFinding(UnmappedAccountRule.CODE, UnmappedAccountRule.VERSION,
                    UnmappedAccountRule.SEVERITY, UnmappedAccountRule.target(c.account()), description, proofs));
        }
    }

    private static AssessedFinding overrun(YearMonth month, BudgetVsActualResponse r) {
        var rule = r.rule20();
        Map<UUID, BudgetVsActualLineResponse> lines = r.groups().stream().flatMap(g -> g.lines().stream())
                .collect(Collectors.toMap(BudgetVsActualLineResponse::lineId, Function.identity()));
        List<Evidence> proofs = new ArrayList<>();
        for (var l : rule.lines()) {
            BudgetVsActualLineResponse lineItem = lines.get(l.lineId());
            proofs.add(new Evidence(r.budget().fileId(), r.budget().sha256(), lineItem == null ? null : lineItem.page(),
                    "PO, linha " + l.code() + " " + l.description() + ": previsto R$ "
                            + (lineItem == null ? "—" : MoneyFormatter.format(lineItem.planned())) + ", realizado R$ "
                            + (lineItem == null ? "—" : MoneyFormatter.format(lineItem.actual())) + ", acima do previsto R$ "
                            + MoneyFormatter.format(l.overrun()), l.lineId()));
        }
        for (UsedCashFlowResponse f : r.months().isEmpty() ? List.<UsedCashFlowResponse>of() : r.months().getFirst().cashFlows()) {
            proofs.add(new Evidence(f.fileId(), f.sha256(), null, "Fluxo de caixa " + f.name() + " ("
                    + BudgetVsActualCalculator.mmyyyy(month) + ")", null));
        }
        String description = "excesso de " + percentage(rule.percentage()) + " do previsto do mês; a Conv. 16.2 exige"
                + " aprovação em AGE para o excedente; verificar ata. " + BudgetVsActualCalculator.mmyyyy(month)
                + ", fundo Condomínio: excesso R$ " + MoneyFormatter.format(rule.overrun()) + " somado em "
                + rule.linesAbove() + (rule.linesAbove() == 1 ? " linha" : " linhas") + " acima do previsto;"
                + " previsto do mês R$ " + MoneyFormatter.format(rule.monthlyPlanned()) + "; limite de "
                + percentage(rule.limitPercentage()) + ": R$ " + MoneyFormatter.format(rule.limit()) + ".";
        return new AssessedFinding(MonthlyOverrunRule.CODE, MonthlyOverrunRule.VERSION, MonthlyOverrunRule.SEVERITY,
                OPERATING_FUND_TARGET, description, proofs);
    }

    public static String percentage(BigDecimal v) {
        BigDecimal p = v.stripTrailingZeros();
        if (p.scale() < 0) {
            p = p.setScale(0);
        }
        return p.toPlainString().replace('.', ',') + "%";
    }
}
