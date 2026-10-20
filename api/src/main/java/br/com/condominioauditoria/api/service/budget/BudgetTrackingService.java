package br.com.condominioauditoria.api.service.budget;

import br.com.condominioauditoria.api.dto.response.budget.BudgetTrackingResponse;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.condominium.Condominium;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import br.com.condominioauditoria.api.service.calculator.BudgetTrackingGrid;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.Cumulative;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Tracking grid of the budget (RF-13.4; ADR 0007, Decision 3): builds the input from the database and calls the pure
 * function {@link BudgetTrackingGrid}. Nothing is stored (ADR 0004, Decision 5).
 */
@Service
public class BudgetTrackingService {

    private final CondominiumRepository condominiums;
    private final BudgetVsActualQueryService query;
    private final SpendingProfileService spendingProfiles;

    public BudgetTrackingService(CondominiumRepository condominiums, BudgetVsActualQueryService query,
            SpendingProfileService spendingProfiles) {
        this.condominiums = condominiums;
        this.query = query;
        this.spendingProfiles = spendingProfiles;
    }

    /** {@code budgetId}: budget version; without it, the most recent confirmed one. */
    @Transactional(readOnly = true)
    public BudgetTrackingResponse get(UUID condominiumId, UUID budgetId) {
        return calculate(condominiumId, budgetId).result();
    }

    public BudgetTrackingGrid.Grid calculate(UUID condominiumId, UUID budgetId) {
        Condominium condominium = condominiums.findById(condominiumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condomínio não encontrado"));
        Budget budget = query.chooseBudget(condominiumId, new Cumulative(), budgetId).orElse(null);
        var base = query.input(condominium, budget, new Cumulative());
        Map<UUID, java.time.LocalDate> partial = query.selectCashFlows(condominiumId).partialUntil();
        return BudgetTrackingGrid.calculate(new BudgetTrackingGrid.Input(base, partial,
                spendingProfiles.profiles(base.lines())));
    }
}
