package br.com.condominioauditoria.api.service.accounting;

import br.com.condominioauditoria.api.dto.response.accounting.FundMovementsResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualResponse;
import br.com.condominioauditoria.api.dto.response.budget.FundResultResponse;
import br.com.condominioauditoria.api.model.accounting.Fund;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.condominium.Condominium;
import br.com.condominioauditoria.api.model.enums.BudgetVsActualStatus;
import br.com.condominioauditoria.api.repository.accounting.FundBalanceRepository;
import br.com.condominioauditoria.api.repository.accounting.FundRepository;
import br.com.condominioauditoria.api.repository.accounting.LedgerEntryRepository;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import br.com.condominioauditoria.api.service.budget.BudgetVsActualQueryService;
import br.com.condominioauditoria.api.service.calculator.BudgetValidity;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.CashFlowFile;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.Cumulative;
import br.com.condominioauditoria.api.service.calculator.FundMovements;
import br.com.condominioauditoria.api.service.calculator.PartialCashFlows;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Every fund of the cash flows, month by month (RF-13.5; ADR 0007, Decision 4): builds the input from the database
 * (printed positions, entries, the budget vs. actual of each month for the operating fund's debits and the planned
 * amounts) and calls the pure function {@link FundMovements}. Nothing is stored.
 */
@Service
public class FundMovementService {

    /** Longest period of one query (three fiscal years). */
    static final int MAX_MONTHS = 36;

    private final CondominiumRepository condominiums;
    private final FundRepository funds;
    private final FundBalanceRepository balances;
    private final LedgerEntryRepository entries;
    private final BudgetVsActualQueryService budgetVsActual;

    public FundMovementService(CondominiumRepository condominiums, FundRepository funds, FundBalanceRepository balances,
            LedgerEntryRepository entries, BudgetVsActualQueryService budgetVsActual) {
        this.condominiums = condominiums;
        this.funds = funds;
        this.balances = balances;
        this.entries = entries;
        this.budgetVsActual = budgetVsActual;
    }

    /**
     * {@code from}/{@code to}: YYYY-MM; without them, the fiscal year of the most recent confirmed budget.
     */
    @Transactional(readOnly = true)
    public FundMovementsResponse get(UUID condominiumId, String from, String to) {
        YearMonth[] period = period(condominiumId, from, to);
        return calculate(condominiumId, period[0], period[1]);
    }

    public FundMovementsResponse calculate(UUID condominiumId, YearMonth from, YearMonth to) {
        Condominium condominium = condominiums.findById(condominiumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condomínio não encontrado"));
        List<YearMonth> months = BudgetVsActualCalculator.months(from, to);
        PartialCashFlows selection = budgetVsActual.selectCashFlows(condominiumId);
        List<CashFlowFile> cashFlows = selection.effective().stream()
                .filter(f -> months.stream().anyMatch(f::covers)).toList();
        List<UUID> ids = cashFlows.stream().map(CashFlowFile::fileId).toList();
        Map<UUID, String> names = funds.findByCondominiumId(condominiumId).stream()
                .collect(Collectors.toMap(Fund::getId, Fund::getName));

        Map<YearMonth, FundMovements.OperatingFundDebits> operating = new HashMap<>();
        Map<YearMonth, Map<UUID, BigDecimal>> planned = new HashMap<>();
        Map<UUID, FundMovements.FundLine> fundLines = new LinkedHashMap<>();
        for (YearMonth m : months) {
            if (cashFlows.stream().noneMatch(f -> f.covers(m))) {
                continue;
            }
            BudgetVsActualResponse r = budgetVsActual.calculate(condominiumId, m.toString(), null).result();
            if (r.status() != BudgetVsActualStatus.CALCULATED) {
                continue;
            }
            operating.put(m, new FundMovements.OperatingFundDebits(r.totals().actualExpense(),
                    r.cashFlowCheck().transfers(), r.adjustments().total()));
            Map<UUID, BigDecimal> byFund = new HashMap<>();
            for (FundResultResponse f : r.funds()) {
                if (f.fundId() != null && f.lineId() != null && f.planned() != null) {
                    byFund.put(f.fundId(), f.planned());
                    fundLines.putIfAbsent(f.fundId(), new FundMovements.FundLine(f.lineId(), f.lineCode()));
                }
            }
            planned.put(m, FundMovements.planned(condominium.getOperatingFundId(), r.totals().monthlyPlanned(),
                    byFund));
        }
        return FundMovements.calculate(new FundMovements.Input(months, cashFlows, selection.partialUntil(),
                ids.isEmpty() ? List.of() : balances.findByFileIdIn(ids),
                ids.isEmpty() ? List.of() : entries.findByFileIdInAndDateBetween(ids, from.atDay(1), to.atEndOfMonth()),
                names, condominium.getOperatingFundId(), operating, planned, fundLines));
    }

    /** The given period, or the fiscal year of the most recent confirmed budget; 400 when invalid or too long. */
    public YearMonth[] period(UUID condominiumId, String from, String to) {
        YearMonth start = month(from, "from");
        YearMonth end = month(to, "to");
        if (start == null || end == null) {
            Budget budget = budgetVsActual.chooseBudget(condominiumId, new Cumulative(), null).orElse(null);
            BudgetValidity v = budget == null ? null : BudgetValidity.of(budget).orElse(null);
            if (v == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Informe o período (from e to, AAAA-MM): não há PO confirmada para usar o exercício");
            }
            start = start == null ? v.start() : start;
            end = end == null ? v.end() : end;
        }
        if (end.isBefore(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O fim do período (" + end
                    + ") é anterior ao início (" + start + ")");
        }
        if (BudgetVsActualCalculator.months(start, end).size() > MAX_MONTHS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Período maior que " + MAX_MONTHS + " meses");
        }
        return new YearMonth[] {start, end};
    }

    private static YearMonth month(String text, String name) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " deve ser AAAA-MM: " + text);
        }
    }
}
