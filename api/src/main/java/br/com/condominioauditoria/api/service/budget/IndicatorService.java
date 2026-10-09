package br.com.condominioauditoria.api.service.budget;

import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualResponse;
import br.com.condominioauditoria.api.dto.response.budget.FiscalYearComparisonResponse;
import br.com.condominioauditoria.api.dto.response.budget.IndicatorsResponse;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.condominium.Condominium;
import br.com.condominioauditoria.api.model.enums.BudgetStatus;
import br.com.condominioauditoria.api.model.enums.BudgetVsActualStatus;
import br.com.condominioauditoria.api.model.enums.MonthStatus;
import br.com.condominioauditoria.api.repository.budget.BudgetRepository;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import br.com.condominioauditoria.api.service.calculator.BudgetIndicators;
import br.com.condominioauditoria.api.service.calculator.BudgetIndicators.CalculatedMonth;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.CashFlowFile;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Builds the {@link BudgetIndicators} input from the database (RF-11.10 to RF-11.12): the fiscal year cumulative and
 * one calculation per month with a cash flow, through the same query as the budget vs. actual screen, and the
 * comparison with the previous fiscal year (RF-11.6). Nothing is stored.
 */
@Service
public class IndicatorService {

    private final CondominiumRepository condominiums;
    private final BudgetRepository budgets;
    private final BudgetVsActualQueryService budgetVsActual;
    private final FiscalYearService fiscalYears;
    private final FiscalYearComparisonService comparison;

    public IndicatorService(CondominiumRepository condominiums, BudgetRepository budgets,
            BudgetVsActualQueryService budgetVsActual, FiscalYearService fiscalYears,
                    FiscalYearComparisonService comparison) {
        this.condominiums = condominiums;
        this.budgets = budgets;
        this.budgetVsActual = budgetVsActual;
        this.fiscalYears = fiscalYears;
        this.comparison = comparison;
    }

    /** Null {@code budgetId} = the most recent fiscal year; null {@code fundId} = all. */
    @Transactional(readOnly = true)
    public IndicatorsResponse indicators(UUID condominiumId, UUID budgetId, UUID fundId) {
        Condominium condominium = condominiums.findById(condominiumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condomínio não encontrado"));
        if (fundId != null) {
            budgetVsActual.filterFund(condominiumId, fundId);
        }
        List<String> ids = fiscalYears.ids(condominiumId);
        UUID chosen = budgetId != null ? budgetId : ids.stream().filter(id -> id.startsWith("po:")).findFirst()
                .map(id -> UUID.fromString(id.substring(3)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nenhuma PO confirmada"));
        Budget budget = budgets.findByIdAndCondominiumId(chosen, condominiumId)
                .filter(p -> p.getStatus() == BudgetStatus.CONFIRMADA && p.getFiscalYearStart() != null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PO confirmada não encontrada"));

        BudgetVsActualResponse cumulative = budgetVsActual.calculate(condominiumId, "acumulado",
                budget.getId()).result();
        List<CashFlowFile> cashFlows = budgetVsActual.cashFlows(condominiumId);
        List<CalculatedMonth> months = new ArrayList<>();
        for (YearMonth m = budget.getFiscalYearStart(); !m.isAfter(budget.getFiscalYearEnd()); m = m.plusMonths(1)) {
            MonthStatus status = FiscalYearService.status(m, cashFlows);
            BudgetVsActualResponse ofMonth = null;
            if (status == MonthStatus.COM_FLUXO) {
                BudgetVsActualResponse r = budgetVsActual.calculate(condominiumId, m.toString(),
                        budget.getId()).result();
                ofMonth = r.status() == BudgetVsActualStatus.CALCULADO ? r : null;
            }
            months.add(new CalculatedMonth(m, status, ofMonth));
        }

        // Chart 7: this fiscal year and the previous one in the list (budget or printed column)
        int i = ids.indexOf("po:" + budget.getId());
        FiscalYearComparisonResponse compared = null;
        String withoutComparison = null;
        if (i >= 0 && i + 1 < ids.size()) {
            compared = comparison.compare(condominiumId, List.of(ids.get(i), ids.get(i + 1)), fundId, false);
        } else {
            withoutComparison = "Comparação entre exercícios: sem exercício anterior a este";
        }
        return BudgetIndicators.build(new BudgetIndicators.Input(budget,
                FiscalYearService.label(budget.getFiscalYearStart(),
                budget.getFiscalYearEnd()), cumulative, List.copyOf(months), fundId, condominium.getOperatingFundId(),
                compared, withoutComparison));
    }
}
