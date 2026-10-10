package br.com.condominioauditoria.api.service.calculator;

import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualWarningResponse;
import br.com.condominioauditoria.api.dto.response.budget.EvidenceResponse;
import br.com.condominioauditoria.api.dto.response.budget.FiscalYearMonthResponse;
import br.com.condominioauditoria.api.model.enums.BudgetVsActualStatus;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.Calculation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * "Fundo" filter of budget vs. actual (RF-03.1.13), applied to the already calculated result: nothing is recalculated,
 * only hidden. Condomínio fund (the operating fund): groups, lines, blocks, check and the 20% rule, without the funds
 * panel. Another fund: only that fund's panel (collection × planned, or movement without planned) and its evidence;
 * the Condomínio fund numbers are null and the result is never "provisório". Pure function.
 */
public final class FundView {

    /** Warnings that only concern the Condomínio fund. */
    private static final Set<String> OPERATING_FUND_WARNINGS = Set.of("NO_CONFIRMED_MAPPING", "ENTRY_WITHOUT_ACCOUNT",
            "TO_REALLOCATE", "REALLOCATION_WITHOUT_ENTRY", "REALLOCATION_WITHOUT_EFFECT", "CASH_FLOW_CHECK",
            "RULE_NOT_EVALUATED");
    private static final Set<String> FUND_WARNINGS = Set.of("LINE_WITHOUT_FUND", "REPROCESS_CASH_FLOW");

    private FundView() {
    }

    /** Null {@code fundId}: no filter (everything). */
    public static Calculation filter(Calculation c, UUID fundId, UUID operatingFundId) {
        BudgetVsActualResponse r = c.result();
        if (fundId == null || r.status() != BudgetVsActualStatus.CALCULATED) {
            return c;
        }
        if (fundId.equals(operatingFundId)) {
            Map<String, List<EvidenceResponse>> ev = c.evidence().entrySet().stream()
                    .filter(x -> !x.getKey().startsWith("fund:"))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a,
                            java.util.TreeMap::new));
            return new Calculation(new BudgetVsActualResponse(r.calculationVersion(), r.period(), r.status(),
                    r.message(), r.budget(),
                    r.months(), r.summedMonths(), r.monthsWithoutCashFlow(), r.monthsWithTwoCashFlows(), r.mapping(),
                            r.provisional(),
                    r.totals(), r.groups(), r.adjustments(), r.toReallocate(), r.withoutBudgetLine(),
                            r.cashFlowCheck(), r.rule20(),
                    List.of(), r.warnings().stream().filter(a -> !FUND_WARNINGS.contains(a.code())).toList()), ev);
        }
        String target = BudgetVsActualCalculator.fundTarget(fundId);
        List<BudgetVsActualWarningResponse> warnings = r.warnings().stream().filter(a -> !OPERATING_FUND_WARNINGS.contains(a.code())).toList();
        List<FiscalYearMonthResponse> months = r.months().stream().map(m -> new FiscalYearMonthResponse(m.month(),
                m.status(), m.cashFlows(),
                null, null, null, null, null, m.extended())).toList();
        return new Calculation(new BudgetVsActualResponse(r.calculationVersion(), r.period(), r.status(), r.message(),
                r.budget(),
                months, r.summedMonths(), r.monthsWithoutCashFlow(), r.monthsWithTwoCashFlows(), null, false, null,
                        List.of(), null,
                null, null, null, null, r.funds().stream().filter(f -> fundId.equals(f.fundId())).toList(), warnings),
                c.evidence().containsKey(target) ? Map.of(target, c.evidence().get(target)) : Map.of());
    }
}
