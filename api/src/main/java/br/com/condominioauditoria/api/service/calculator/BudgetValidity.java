package br.com.condominioauditoria.api.service.calculator;

import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.enums.BudgetStatus;
import java.time.YearMonth;
import java.util.Collection;
import java.util.Optional;

/**
 * Months in which a budget is valid (RF-03.1.3: only one budget per month for each condominium). Confirmed: the whole
 * fiscal year. Superseded: from the fiscal year start until the month before the new version. Pure function.
 *
 * <p>Extension (RF-11.3; ADR 0005, Decision 4): the confirmed budget is also valid from the month after the end of the
 * fiscal year until the month marked by the Admin, but only when no confirmed budget covers the month by its fiscal
 * year. The extended months are not part of the fiscal year: {@link #of} does not include them.
 */
public record BudgetValidity(Budget budget, YearMonth start, YearMonth end) {

    public static Optional<BudgetValidity> of(Budget p) {
        if (!p.getStatus().isLocked() || p.getFiscalYearStart() == null) {
            return Optional.empty();
        }
        YearMonth end = p.getFiscalYearEnd();
        if (p.getStatus() == BudgetStatus.SUPERSEDED && p.getSupersededFrom() != null) {
            YearMonth before = p.getSupersededFrom().minusMonths(1);
            end = before.isBefore(end) ? before : end;
        }
        if (end.isBefore(p.getFiscalYearStart())) {
            return Optional.empty();
        }
        return Optional.of(new BudgetValidity(p, p.getFiscalYearStart(), end));
    }

    /**
     * Extended months of the budget (from the month after the end of the fiscal year until the extension), or empty.
     */
    public static Optional<BudgetValidity> extension(Budget p) {
        if (p.getStatus() != BudgetStatus.CONFIRMED || p.getFiscalYearEnd() == null || p.getExtendedUntil() == null
                || !p.getExtendedUntil().isAfter(p.getFiscalYearEnd())) {
            return Optional.empty();
        }
        return Optional.of(new BudgetValidity(p, p.getFiscalYearEnd().plusMonths(1), p.getExtendedUntil()));
    }

    /** Budget of the month and whether it is valid by extension ("PO prorrogada"). */
    public record BudgetOfMonth(Budget budget, boolean extended) {
    }

    /**
     * Budget of the month (ADR 0005, Decision 4): (1) the confirmed budget whose fiscal year covers the month;
     * otherwise (2) the budget whose extension covers the month; otherwise empty ("sem PO aprovada para este mês").
     */
    public static Optional<BudgetOfMonth> ofMonth(Collection<Budget> budgets, YearMonth month) {
        Optional<Budget> byFiscalYear = budgets.stream().map(BudgetValidity::of).flatMap(Optional::stream)
                .filter(v -> v.covers(month)).map(BudgetValidity::budget).findFirst();
        if (byFiscalYear.isPresent()) {
            return Optional.of(new BudgetOfMonth(byFiscalYear.get(), false));
        }
        return budgets.stream().map(BudgetValidity::extension).flatMap(Optional::stream).filter(v -> v.covers(month))
                .map(v -> new BudgetOfMonth(v.budget(), true)).findFirst();
    }

    public boolean covers(YearMonth month) {
        return !month.isBefore(start) && !month.isAfter(end);
    }

    public boolean overlaps(YearMonth otherStart, YearMonth otherEnd) {
        return !otherEnd.isBefore(start) && !otherStart.isAfter(end);
    }

    /** The budget valid in the month, by fiscal year or by extension, or empty ("sem PO aprovada para este mês"). */
    public static Optional<Budget> activeInMonth(Collection<Budget> budgets, YearMonth month) {
        return ofMonth(budgets, month).map(BudgetOfMonth::budget);
    }

    public String period() {
        return start + " a " + end;
    }
}
