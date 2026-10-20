package br.com.condominioauditoria.api.service.budget;

import br.com.condominioauditoria.api.dto.response.budget.BalanceBridgeResponse;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.condominium.Condominium;
import br.com.condominioauditoria.api.model.enums.BudgetVsActualStatus;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import br.com.condominioauditoria.api.service.accounting.FundMovementService;
import br.com.condominioauditoria.api.service.calculator.BalanceBridge;
import br.com.condominioauditoria.api.service.calculator.BudgetValidity;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.Cumulative;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.Month;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Balance bridge (RF-13.6 and RF-13.7; ADR 0007, Decision 3): the tracking grid of the budget valid in the period and
 * the fund movements of the same months (and of the month before, for the opening balance), given to the pure function
 * {@link BalanceBridge}. Nothing is stored.
 */
@Service
public class BalanceBridgeService {

    public static final String ALL_FUNDS = "all";

    private final CondominiumRepository condominiums;
    private final BudgetVsActualQueryService query;
    private final BudgetTrackingService tracking;
    private final FundMovementService fundMovements;

    public BalanceBridgeService(CondominiumRepository condominiums, BudgetVsActualQueryService query,
            BudgetTrackingService tracking, FundMovementService fundMovements) {
        this.condominiums = condominiums;
        this.query = query;
        this.tracking = tracking;
        this.fundMovements = fundMovements;
    }

    /**
     * {@code fund}: a fund id, "all" (every fund) or empty (the operating fund). {@code from}/{@code to}: YYYY-MM inside
     * one fiscal year; without them, the fiscal year of the most recent confirmed budget (or of the budget valid in
     * {@code from}).
     */
    @Transactional(readOnly = true)
    public BalanceBridgeResponse get(UUID condominiumId, String fund, String from, String to) {
        Condominium condominium = condominiums.findById(condominiumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condomínio não encontrado"));
        boolean all = fund != null && fund.trim().equalsIgnoreCase(ALL_FUNDS);
        UUID fundId = null;
        if (!all) {
            if (fund == null || fund.isBlank()) {
                fundId = condominium.getOperatingFundId();
            } else {
                try {
                    fundId = query.filterFund(condominiumId, UUID.fromString(fund.trim())).getId();
                } catch (IllegalArgumentException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "fund deve ser o id de um fundo ou \"all\": " + fund);
                }
            }
        }
        YearMonth[] period = fundMovements.period(condominiumId, from, to);
        Budget budget = query.chooseBudget(condominiumId, from == null || from.isBlank() ? new Cumulative()
                : new Month(period[0]), null).orElse(null);
        if (budget == null) {
            // No budget valid in "from" (e.g. a month before the fiscal year): the grid uses the most recent confirmed
            // budget, so the period is checked against its fiscal year
            budget = query.chooseBudget(condominiumId, new Cumulative(), null).orElse(null);
        }
        var gridResult = tracking.calculate(condominiumId, budget == null ? null : budget.getId());
        var grid = gridResult.result();
        if (!all && fundId == null) {
            return new BalanceBridgeResponse(BalanceBridge.VERSION, BudgetVsActualStatus.NO_OPERATING_FUND,
                    "Confirme o fundo ordinário (fundo Condomínio) para calcular a origem do saldo", null, null, false,
                    period[0].toString(), period[1].toString(), grid.budget(), null, null, null, null, null, List.of(),
                    List.of(), List.of(), null, null, null, null, null);
        }
        BudgetValidity validity = budget == null ? null : BudgetValidity.of(budget).orElse(null);
        if (validity != null && (period[0].isBefore(validity.start()) || period[1].isAfter(validity.end()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O período da ponte (" + period[0] + " a "
                    + period[1] + ") deve estar dentro do exercício da PO (" + validity.start() + " a "
                    + validity.end() + ")");
        }
        var funds = fundMovements.calculate(condominiumId, period[0].minusMonths(1), period[1]);
        return BalanceBridge.calculate(BalanceBridge.Input.of(gridResult, funds, all ? null : fundId,
                condominium.getOperatingFundId(), period[0], period[1]));
    }
}
