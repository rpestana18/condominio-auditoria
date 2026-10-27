package br.com.condominioauditoria.api.service.accounting;

import br.com.condominioauditoria.api.dto.response.accounting.CashFlowEntriesResponse;
import br.com.condominioauditoria.api.dto.response.accounting.CashFlowEntryResponse;
import br.com.condominioauditoria.api.dto.response.accounting.CashFlowMonthlyResponse;
import br.com.condominioauditoria.api.dto.response.accounting.FundMovementsResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetBriefResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualWarningResponse;
import br.com.condominioauditoria.api.dto.response.budget.LineReviewStateResponse;
import br.com.condominioauditoria.api.dto.response.budget.UsedCashFlowResponse;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.condominium.Condominium;
import br.com.condominioauditoria.api.model.enums.BudgetVsActualStatus;
import br.com.condominioauditoria.api.model.enums.CashFlowRowCode;
import br.com.condominioauditoria.api.repository.accounting.LedgerEntryRepository;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import br.com.condominioauditoria.api.service.budget.BudgetVsActualQueryService;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.CashFlowFile;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.Calculation;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.Cumulative;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.Month;
import br.com.condominioauditoria.api.service.calculator.MonthlyCashFlow;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * "Entradas e saídas" (RF-13.11 and RF-13.16; ADR 0009, Decision 4): the fund movements, the budget vs. actual of each
 * month of the budget and the entries of the cash flows, given to the pure function {@link MonthlyCashFlow}. Without a
 * period, from the first to the last month with a cash flow loaded, also before the budget. Nothing is stored.
 */
@Service
public class CashFlowService {

    public static final String ALL_FUNDS = "all";

    private final CondominiumRepository condominiums;
    private final BudgetVsActualQueryService query;
    private final FundMovementService fundMovements;
    private final LedgerEntryRepository entries;

    public CashFlowService(CondominiumRepository condominiums, BudgetVsActualQueryService query,
            FundMovementService fundMovements, LedgerEntryRepository entries) {
        this.condominiums = condominiums;
        this.query = query;
        this.fundMovements = fundMovements;
        this.entries = entries;
    }

    /** {@code fund}: a fund id, "all" or empty (the operating fund). {@code from}/{@code to}: YYYY-MM. */
    @Transactional(readOnly = true)
    public CashFlowMonthlyResponse monthly(UUID condominiumId, String fund, String from, String to) {
        Selection s = select(condominiumId, fund, from, to);
        if (s.message() != null) {
            return new CashFlowMonthlyResponse(MonthlyCashFlow.VERSION, s.fundId(), null, s.all(), null, null, null,
                    null, List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of(new BudgetVsActualWarningResponse(s.code(), s.message())));
        }
        return MonthlyCashFlow.calculate(input(condominiumId, s));
    }

    /**
     * Entries of the table with their destination. {@code month}: YYYY-MM (one month) or empty (the whole period);
     * {@code line}: a budget line; {@code block}: a row code; {@code account}: a cash flow account code.
     */
    @Transactional(readOnly = true)
    public CashFlowEntriesResponse entries(UUID condominiumId, String fund, String month, UUID line, String block,
            String account, String from, String to) {
        YearMonth m = month == null || month.isBlank() ? null : month(month, "month");
        CashFlowRowCode code = null;
        if (block != null && !block.isBlank()) {
            try {
                code = CashFlowRowCode.valueOf(block.trim());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "block inválido: " + block);
            }
        }
        Selection s = select(condominiumId, fund, m == null ? from : m.toString(), m == null ? to : m.toString());
        String acc = account == null || account.isBlank() ? null : account.trim();
        if (s.message() != null) {
            return new CashFlowEntriesResponse(s.fundId(), s.all(), m == null ? null : m.toString(), line, code, acc,
                    0, null, s.message(), List.of());
        }
        List<CashFlowEntryResponse> list = MonthlyCashFlow.entries(input(condominiumId, s),
                new MonthlyCashFlow.EntryFilter(m, line, code, acc));
        BigDecimal total = list.stream().map(CashFlowEntryResponse::amount).reduce(BigDecimal.ZERO.setScale(2),
                BigDecimal::add);
        return new CashFlowEntriesResponse(s.fundId(), s.all(), m == null ? null : m.toString(), line, code, acc,
                list.size(), total, list.isEmpty() && m != null ? "Nenhum lançamento com estes filtros em "
                        + BudgetVsActualCalculator.mmyyyy(m) : null, list);
    }

    private record Selection(UUID fundId, boolean all, UUID operatingFundId, YearMonth from, YearMonth to, String code,
            String message) {
    }

    private Selection select(UUID condominiumId, String fund, String from, String to) {
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
        UUID operating = condominium.getOperatingFundId();
        if (!all && fundId == null) {
            return new Selection(null, false, null, null, null, "NO_OPERATING_FUND",
                    "Confirme o fundo ordinário (fundo Condomínio) para ver as entradas e saídas");
        }
        YearMonth start = from == null || from.isBlank() ? null : month(from, "from");
        YearMonth end = to == null || to.isBlank() ? null : month(to, "to");
        if (start == null || end == null) {
            List<CashFlowFile> files = query.selectCashFlows(condominiumId).effective().stream()
                    .filter(f -> f.periodStart() != null && f.periodEnd() != null).toList();
            if (files.isEmpty()) {
                return new Selection(fundId, all, operating, null, null, "NO_CASH_FLOW",
                        "Nenhum extrato carregado: envie os balancetes e fluxos de caixa em Arquivos");
            }
            start = start == null ? files.stream().map(f -> YearMonth.from(f.periodStart())).min(YearMonth::compareTo)
                    .orElseThrow() : start;
            end = end == null ? files.stream().map(f -> YearMonth.from(f.periodEnd())).max(YearMonth::compareTo)
                    .orElseThrow() : end;
        }
        if (end.isBefore(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O fim do período (" + end
                    + ") é anterior ao início (" + start + ")");
        }
        if (BudgetVsActualCalculator.months(start, end).size() > FundMovementService.MAX_MONTHS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Período maior que "
                    + FundMovementService.MAX_MONTHS + " meses");
        }
        return new Selection(fundId, all, operating, start, end, null, null);
    }

    private MonthlyCashFlow.Input input(UUID condominiumId, Selection s) {
        List<YearMonth> months = BudgetVsActualCalculator.months(s.from(), s.to());
        FundMovementsResponse funds = fundMovements.calculate(condominiumId, s.from(), s.to());
        Map<YearMonth, Calculation> calculations = new HashMap<>();
        Set<YearMonth> budgetMonths = new HashSet<>();
        Budget last = null;
        BudgetBriefResponse brief = null;
        for (YearMonth m : months) {
            var budget = query.chooseBudget(condominiumId, new Month(m), null);
            if (budget.isEmpty()) {
                continue;
            }
            budgetMonths.add(m);
            last = budget.get();
            Calculation c = query.calculate(condominiumId, m.toString(), null);
            BudgetVsActualResponse r = c.result();
            if (r.status() == BudgetVsActualStatus.CALCULATED) {
                calculations.put(m, c);
                brief = r.budget();
            }
        }
        if (last == null) {
            last = query.chooseBudget(condominiumId, new Cumulative(), null).orElse(null);
        }
        LineReviewStateResponse review = query.lineReview(last == null ? null : last.getId());
        Set<UUID> files = new LinkedHashSet<>();
        funds.months().forEach(x -> x.cashFlows().stream().map(UsedCashFlowResponse::fileId).forEach(files::add));
        var ledger = files.isEmpty() ? List.<br.com.condominioauditoria.api.model.accounting.LedgerEntry>of()
                : entries.findByFileIdInAndDateBetween(files, s.from().atDay(1), s.to().atEndOfMonth());
        return new MonthlyCashFlow.Input(months, funds, calculations, budgetMonths, ledger, s.fundId(), s.all(),
                s.operatingFundId(), review, brief);
    }

    private static YearMonth month(String text, String name) {
        try {
            return YearMonth.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " deve ser AAAA-MM: " + text);
        }
    }
}
