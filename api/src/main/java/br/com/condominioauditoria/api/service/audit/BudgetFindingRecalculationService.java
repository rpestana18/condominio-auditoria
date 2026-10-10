package br.com.condominioauditoria.api.service.audit;

import br.com.condominioauditoria.api.event.BudgetChanged;
import br.com.condominioauditoria.api.model.enums.BudgetStatus;
import br.com.condominioauditoria.api.repository.budget.BudgetRepository;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import br.com.condominioauditoria.api.service.audit.FindingSyncService.SyncResult;
import br.com.condominioauditoria.api.service.budget.BudgetVsActualQueryService;
import br.com.condominioauditoria.api.service.calculator.BudgetValidity;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator;
import br.com.condominioauditoria.api.service.calculator.MonthFindings;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Recalculates a condominium's budget findings after each change (ADR 0004, Decision 5; RF-03.1.12): for each month of
 * a confirmed budget (fiscal year and extended months, RF-11.3), uses the same calculation as the screen and saves by
 * the unique key. A month without numbers (no cash flow, two cash flows) changes no finding. Called by
 * {@link BudgetFindingRecalculationListener} after the commit; the caller opens the transaction.
 */
@Service
public class BudgetFindingRecalculationService {

    private static final Logger log = LoggerFactory.getLogger(BudgetFindingRecalculationService.class);

    private final CondominiumRepository condominiums;
    private final BudgetRepository budgets;
    private final BudgetVsActualQueryService query;
    private final FindingSyncService findingSync;

    public BudgetFindingRecalculationService(CondominiumRepository condominiums, BudgetRepository budgets,
            BudgetVsActualQueryService query, FindingSyncService findingSync) {
        this.condominiums = condominiums;
        this.budgets = budgets;
        this.query = query;
        this.findingSync = findingSync;
    }

    /** Result per recalculated month (months without numbers are left out). */
    public Map<YearMonth, SyncResult> recalculate(BudgetChanged change) {
        UUID condominiumId = change.condominiumId();
        // One recalculation at a time per condominium (select ... for update): two concurrent recalculations do not
        // duplicate
        if (condominiums.lockById(condominiumId).isEmpty()) {
            return Map.of();
        }
        Set<YearMonth> months = new TreeSet<>();
        budgets.findByCondominiumIdAndStatusIn(condominiumId,
                        EnumSet.of(BudgetStatus.CONFIRMED, BudgetStatus.SUPERSEDED)).stream()
                .flatMap(p -> java.util.stream.Stream.of(BudgetValidity.of(p), BudgetValidity.extension(p)))
                .flatMap(java.util.Optional::stream)
                .forEach(v -> months.addAll(BudgetVsActualCalculator.months(v.start(), v.end())));
        Map<YearMonth, SyncResult> result = new LinkedHashMap<>();
        for (YearMonth month : months) {
            MonthFindings findings = MonthFindings.assess(month, query.calculate(condominiumId, month.toString(),
                    null));
            if (findings.rules().isEmpty()) {
                continue;
            }
            SyncResult s = findingSync.synchronize(condominiumId, month, findings.rules(), findings.assessed(),
                    change.trigger());
            result.put(month, s);
            if (s.opened() + s.reopened() + s.closed() > 0) {
                log.info("Achados de {} no condomínio {}: {} abertos, {} reabertos, {} não se aplicam mais ({})", month,
                        condominiumId, s.opened(), s.reopened(), s.closed(), change.trigger().text());
            }
        }
        return result;
    }
}
