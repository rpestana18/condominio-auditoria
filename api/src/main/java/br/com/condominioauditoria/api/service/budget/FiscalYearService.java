package br.com.condominioauditoria.api.service.budget;

import br.com.condominioauditoria.api.dto.response.budget.FiscalYearMonthSummaryResponse;
import br.com.condominioauditoria.api.dto.response.budget.FiscalYearResponse;
import br.com.condominioauditoria.api.dto.response.budget.GroupDifferenceResponse;
import br.com.condominioauditoria.api.dto.response.budget.PrintedColumnCheckResponse;
import br.com.condominioauditoria.api.mapper.BudgetMapper;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.enums.AccountMappingFilter;
import br.com.condominioauditoria.api.model.enums.BudgetItemFilter;
import br.com.condominioauditoria.api.model.enums.BudgetStatus;
import br.com.condominioauditoria.api.model.enums.FiscalYearType;
import br.com.condominioauditoria.api.model.enums.MonthStatus;
import br.com.condominioauditoria.api.repository.budget.BudgetLineRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetRepository;
import br.com.condominioauditoria.api.service.calculator.BudgetStructure;
import br.com.condominioauditoria.api.service.calculator.BudgetValidity;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.CashFlowFile;
import br.com.condominioauditoria.api.service.calculator.PrintedColumn;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The condominium's fiscal years for the "Análise da PO" menu (RF-11.4 and RF-11.5; ADR 0005, Decisions 3 and 5).
 * Nothing is stored: the list and the printed column are built on each query.
 *
 * <p>Previous fiscal year of a budget X: the confirmed budget whose fiscal year covers the month before the start of
 * X (extended months do not count). Without it, X's "Orçado anterior" column becomes the fiscal year "AAAA/AAAA
 * (coluna impressa)", planned only. When the previous budget is confirmed later, it takes the column's place with no
 * extra action, and the column stays only as a check, with a warning per group when the values differ.
 */
@Service
public class FiscalYearService {

    private final BudgetRepository budgets;
    private final BudgetLineRepository lines;
    private final BudgetVsActualQueryService budgetVsActual;
    private final AccountMappingService accountMappings;
    private final BudgetItemService budgetItems;

    public FiscalYearService(BudgetRepository budgets, BudgetLineRepository lines,
            BudgetVsActualQueryService budgetVsActual, AccountMappingService mapping, BudgetItemService items) {
        this.budgets = budgets;
        this.lines = lines;
        this.budgetVsActual = budgetVsActual;
        this.accountMappings = mapping;
        this.budgetItems = items;
    }

    /**
     * Fiscal years from the most recent to the oldest; the printed column comes right after the budget that printed it.
     */
    @Transactional(readOnly = true)
    public List<FiscalYearResponse> list(UUID condominiumId) {
        List<Budget> confirmed = confirmed(condominiumId);
        List<CashFlowFile> cashFlows = budgetVsActual.cashFlows(condominiumId);
        List<FiscalYearResponse> list = new ArrayList<>();
        for (Budget budget : confirmed) {
            List<BudgetLine> ofBudget = lines.findByBudgetIdOrderByPosition(budget.getId());
            // Column of the next budget that this budget superseded (kept only as a check)
            Optional<Budget> next = confirmed.stream()
                    .filter(x -> previous(confirmed, x).filter(a -> a.getId().equals(budget.getId())).isPresent())
                    .findFirst();
            String supersededColumn = null;
            List<String> warnings = List.of();
            if (next.isPresent()) {
                Optional<PrintedColumn> column = PrintedColumn.of(next.get(),
                        lines.findByBudgetIdOrderByPosition(next.get().getId()));
                if (column.isPresent()) {
                    supersededColumn = columnId(next.get());
                    warnings = column.get().differences(ofBudget, tolerance(next.get())).stream()
                            .map(GroupDifferenceResponse::text).toList();
                }
            }
            list.add(new FiscalYearResponse("budget:" + budget.getId(), FiscalYearType.PO,
                    label(budget.getFiscalYearStart(),
                    budget.getFiscalYearEnd()), budget.getId(), budget.getVersion(),
                            budget.getFiscalYearStart().toString(),
                    budget.getFiscalYearEnd().toString(), BudgetMapper.toExtensionResponse(budget),
                            BudgetStructure.of(ofBudget).monthlyPlannedFromLines()
                            .setScale(2), months(budget, cashFlows),
                    accountMappings.list(condominiumId, budget.getId(), AccountMappingFilter.ALL).summary(),
                    budgetItems.list(condominiumId, budget.getId(), BudgetItemFilter.ALL).summary(), supersededColumn,
                    warnings));
            if (previous(confirmed, budget).isEmpty()) {
                PrintedColumn.of(budget, ofBudget).ifPresent(c -> list.add(columnAsFiscalYear(budget, c)));
            }
        }
        return List.copyOf(list);
    }

    /** Fiscal year ids ("budget:" and "column:"), in list order, without building the summaries. */
    @Transactional(readOnly = true)
    public List<String> ids(UUID condominiumId) {
        List<Budget> confirmed = confirmed(condominiumId);
        List<String> ids = new ArrayList<>();
        for (Budget budget : confirmed) {
            ids.add("budget:" + budget.getId());
            if (previous(confirmed, budget).isEmpty()
                    && PrintedColumn.of(budget, lines.findByBudgetIdOrderByPosition(budget.getId())).isPresent()) {
                ids.add(columnId(budget));
            }
        }
        return List.copyOf(ids);
    }

    /**
     * Check of the budget's "Orçado anterior" column (RF-11.5). 404 if the budget is not confirmed or has no column.
     */
    @Transactional(readOnly = true)
    public PrintedColumnCheckResponse printedColumn(UUID condominiumId, UUID budgetId) {
        Budget budget = budgets.findByIdAndCondominiumId(budgetId, condominiumId)
                .filter(p -> p.getStatus() == BudgetStatus.CONFIRMED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PO confirmada não encontrada"));
        PrintedColumn column = PrintedColumn.of(budget, lines.findByBudgetIdOrderByPosition(budget.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "A PO não tem a coluna \"Orçado anterior\""));
        Optional<Budget> previous = previous(confirmed(condominiumId), budget);
        List<GroupDifferenceResponse> differences = previous.map(a -> column.differences(
                lines.findByBudgetIdOrderByPosition(a.getId()), tolerance(budget))).orElse(List.of());
        List<String> warnings = new ArrayList<>(column.warnings());
        differences.forEach(d -> warnings.add(d.text()));
        return new PrintedColumnCheckResponse(columnId(budget), column.label(), budget.getId(), previous.isPresent(),
                previous.map(Budget::getId).orElse(null),
                previous.map(a -> label(a.getFiscalYearStart(), a.getFiscalYearEnd())).orElse(null),
                column.printedTotal(), column.totalIncludesFunds(), column.funds(), column.monthlyPlanned(),
                column.groups(), differences, List.copyOf(warnings));
    }

    /**
     * Previous fiscal year of the budget: the confirmed budget whose fiscal year covers the month before its start.
     * Fiscal years do not overlap (RF-11.2), so there is at most one.
     */
    public static Optional<Budget> previous(List<Budget> confirmed, Budget budget) {
        YearMonth month = budget.getFiscalYearStart().minusMonths(1);
        return confirmed.stream().filter(p -> !p.getId().equals(budget.getId()))
                .filter(p -> BudgetValidity.of(p).filter(v -> v.covers(month)).isPresent()).findFirst();
    }

    /** "2026/2027" (or "2026", if the fiscal year falls in a single year). */
    public static String label(YearMonth start, YearMonth end) {
        return start.getYear() == end.getYear() ? String.valueOf(start.getYear())
                : start.getYear() + "/" + end.getYear();
    }

    private List<Budget> confirmed(UUID condominiumId) {
        return budgets.findByCondominiumIdAndStatusIn(condominiumId, EnumSet.of(BudgetStatus.CONFIRMED)).stream()
                .filter(p -> p.getFiscalYearStart() != null && p.getFiscalYearEnd() != null)
                .sorted(Comparator.comparing(Budget::getFiscalYearStart).reversed()).toList();
    }

    private static FiscalYearResponse columnAsFiscalYear(Budget budget, PrintedColumn c) {
        YearMonth start = budget.getFiscalYearStart().minusMonths(12);
        YearMonth end = budget.getFiscalYearStart().minusMonths(1);
        return new FiscalYearResponse(columnId(budget), FiscalYearType.PRINTED_COLUMN, c.label(), budget.getId(),
                budget.getVersion(),
                start.toString(), end.toString(), null, c.monthlyPlanned(), List.of(), null, null, null, c.warnings());
    }

    /** Fiscal year months and, after them, the extended ones, with the cash flow situation of each. */
    private static List<FiscalYearMonthSummaryResponse> months(Budget budget, List<CashFlowFile> cashFlows) {
        List<FiscalYearMonthSummaryResponse> months = new ArrayList<>();
        for (YearMonth m = budget.getFiscalYearStart(); !m.isAfter(budget.getFiscalYearEnd()); m = m.plusMonths(1)) {
            months.add(new FiscalYearMonthSummaryResponse(m.toString(), status(m, cashFlows), false));
        }
        BudgetValidity.extension(budget).ifPresent(v -> {
            for (YearMonth m = v.start(); !m.isAfter(v.end()); m = m.plusMonths(1)) {
                months.add(new FiscalYearMonthSummaryResponse(m.toString(), status(m, cashFlows), true));
            }
        });
        return List.copyOf(months);
    }

    public static MonthStatus status(YearMonth month, List<CashFlowFile> cashFlows) {
        long n = cashFlows.stream().filter(f -> f.covers(month)).count();
        return n == 0 ? MonthStatus.NO_CASH_FLOW : n == 1 ? MonthStatus.WITH_CASH_FLOW : MonthStatus.TWO_CASH_FLOWS;
    }

    private static BigDecimal tolerance(Budget budget) {
        return budget.getRoundingTolerance() == null ? new BigDecimal("0.01") : budget.getRoundingTolerance();
    }

    public static String columnId(Budget budget) {
        return "column:" + budget.getId();
    }
}
