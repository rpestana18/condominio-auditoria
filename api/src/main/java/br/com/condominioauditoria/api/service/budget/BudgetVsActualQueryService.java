package br.com.condominioauditoria.api.service.budget;

import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualWarningResponse;
import br.com.condominioauditoria.api.dto.response.budget.EvidenceResponse;
import br.com.condominioauditoria.api.model.accounting.Fund;
import br.com.condominioauditoria.api.model.accounting.LedgerEntry;
import br.com.condominioauditoria.api.model.audit.RuleParameter;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetFundLink;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.budget.Reallocation;
import br.com.condominioauditoria.api.model.condominium.Condominium;
import br.com.condominioauditoria.api.model.enums.BudgetStatus;
import br.com.condominioauditoria.api.model.enums.BudgetWarningCode;
import br.com.condominioauditoria.api.model.enums.FileCategory;
import br.com.condominioauditoria.api.model.enums.FileStatus;
import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.repository.accounting.FundRepository;
import br.com.condominioauditoria.api.repository.accounting.LedgerEntryRepository;
import br.com.condominioauditoria.api.repository.audit.RuleParameterRepository;
import br.com.condominioauditoria.api.repository.budget.AccountMappingRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetFundLinkRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetLineRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetRepository;
import br.com.condominioauditoria.api.repository.budget.ReallocationRepository;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import br.com.condominioauditoria.api.repository.file.SourceFileRepository;
import br.com.condominioauditoria.api.service.audit.rule.MonthlyOverrunRule;
import br.com.condominioauditoria.api.service.calculator.BudgetValidity;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.Calculation;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.CashFlowFile;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.Cumulative;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.Input;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.Month;
import br.com.condominioauditoria.api.service.calculator.BudgetVsActualCalculator.Period;
import br.com.condominioauditoria.api.service.calculator.FundView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Builds the {@link BudgetVsActualCalculator} input from the database and calls the function. Nothing is stored: the
 * numbers are calculated on each query (ADR 0004, Decision 5), from the already processed data (entries, budget,
 * mapping).
 */
@Service
public class BudgetVsActualQueryService {

    /** Cash flow files read (with or without a failing check), as in the dashboard. */
    static final Set<FileStatus> READ_CASH_FLOW = EnumSet.of(FileStatus.CONCLUIDO, FileStatus.PRECISA_REVISAO);
    private static final Set<BudgetWarningCode> BUDGET_WARNINGS = EnumSet.of(BudgetWarningCode.ARREDONDAMENTO,
            BudgetWarningCode.CONFIRMADA_COM_DIVERGENCIA);

    private final CondominiumRepository condominiums;
    private final BudgetRepository budgets;
    private final BudgetLineRepository lines;
    private final AccountMappingRepository mappings;
    private final BudgetFundLinkRepository fundLinks;
    private final FundRepository funds;
    private final SourceFileRepository files;
    private final LedgerEntryRepository ledgerEntries;
    private final RuleParameterRepository ruleParameters;
    private final BudgetQueryService budgetQuery;
    private final ReallocationRepository reallocations;

    public BudgetVsActualQueryService(CondominiumRepository condominiums, BudgetRepository budgets,
            BudgetLineRepository lines, AccountMappingRepository mappings, BudgetFundLinkRepository fundLinks,
            FundRepository funds, SourceFileRepository files, LedgerEntryRepository entries,
            RuleParameterRepository parameters, BudgetQueryService budgetQuery,
            ReallocationRepository reallocations) {
        this.condominiums = condominiums;
        this.budgets = budgets;
        this.lines = lines;
        this.mappings = mappings;
        this.fundLinks = fundLinks;
        this.funds = funds;
        this.files = files;
        this.ledgerEntries = entries;
        this.ruleParameters = parameters;
        this.budgetQuery = budgetQuery;
        this.reallocations = reallocations;
    }

    @Transactional(readOnly = true)
    public BudgetVsActualResponse get(UUID condominiumId, String period, UUID budgetId) {
        return calculate(condominiumId, period, budgetId).result();
    }

    /** With the fund filter (RF-03.1.13); null {@code fundId} = all. */
    @Transactional(readOnly = true)
    public BudgetVsActualResponse get(UUID condominiumId, String period, UUID budgetId, UUID fundId) {
        return calculate(condominiumId, period, budgetId, fundId).result();
    }

    /** The filter's fund, which must belong to this condominium (404 otherwise). */
    public Fund filterFund(UUID condominiumId, UUID fundId) {
        return funds.findByCondominiumId(condominiumId).stream().filter(f -> f.getId().equals(fundId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fundo não encontrado"));
    }

    /** Calculation with the fund filter applied afterwards (it only hides; see {@link FundView}). */
    public Calculation calculate(UUID condominiumId, String period, UUID budgetId, UUID fundId) {
        if (fundId == null) {
            return calculate(condominiumId, period, budgetId);
        }
        filterFund(condominiumId, fundId);
        UUID operatingFund = condominiums.findById(condominiumId).map(Condominium::getOperatingFundId).orElse(null);
        return FundView.filter(calculate(condominiumId, period, budgetId), fundId, operatingFund);
    }

    /**
     * Entries that make up a number: "linha:&lt;id&gt;", "grupo:&lt;id&gt;", "total", "fundo:&lt;id&gt;", AJUSTES,
     * A_REALOCAR, SEM_LINHA_PO, TRANSFERENCIAS.
     */
    @Transactional(readOnly = true)
    public List<EvidenceResponse> evidence(UUID condominiumId, String period, UUID budgetId, String target) {
        if (target == null || target.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o alvo da evidência");
        }
        return BudgetVsActualCalculator.evidence(calculate(condominiumId, period, budgetId), target);
    }

    public Calculation calculate(UUID condominiumId, String periodText, UUID budgetId) {
        Condominium condominium = condominiums.findById(condominiumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condomínio não encontrado"));
        Period period = period(periodText);
        Budget budget = chooseBudget(condominiumId, period, budgetId).orElse(null);
        if (budget == null) {
            return BudgetVsActualCalculator.calculate(new Input(null, null, null, null, null, null,
                    condominium.getOperatingFundId(), null, null, null, null, null, period));
        }
        List<BudgetLine> readChecks = lines.findByBudgetIdOrderByPosition(budget.getId());
        Map<UUID, UUID> fundByLine = fundLinks.findByBudgetId(budget.getId()).stream()
                .collect(Collectors.toMap(BudgetFundLink::getBudgetLineId, BudgetFundLink::getFundId));
        Map<UUID, String> names = funds.findByCondominiumId(condominiumId).stream()
                .collect(Collectors.toMap(Fund::getId, Fund::getName));
        List<CashFlowFile> cashFlows = cashFlows(condominiumId);

        LocalDate start;
        LocalDate end;
        var validity = BudgetValidity.of(budget);
        if (period instanceof Month m) {
            start = m.month().atDay(1);
            end = m.month().atEndOfMonth();
        } else if (validity.isPresent()) {
            // Cumulative: the fiscal year and, after it, the extended months (shown outside the sum, RF-11.3)
            start = validity.get().start().atDay(1);
            end = BudgetValidity.extension(budget).map(BudgetValidity::end).orElse(validity.get().end()).atEndOfMonth();
        } else {
            start = LocalDate.MIN;
            end = LocalDate.MIN;
        }
        List<LedgerEntry> ofPeriod = cashFlows.isEmpty() || start.equals(LocalDate.MIN) ? List.of()
                : ledgerEntries.findByFileIdInAndDateBetween(cashFlows.stream().map(CashFlowFile::fileId).toList(),
                        start,
                        end);
        BigDecimal limit = budget.getFiscalYearStart() == null ? null
                : ruleParameters.findValidOn(condominiumId, MonthlyOverrunRule.PARAMETER, start.equals(LocalDate.MIN)
                        ? budget.getFiscalYearStart().atDay(1) : start).map(RuleParameter::getValue).orElse(null);
        List<BudgetVsActualWarningResponse> budgetWarnings = budgetQuery.detail(budget).warnings().stream()
                .filter(a -> BUDGET_WARNINGS.contains(a.code())).map(a -> new BudgetVsActualWarningResponse(a.code().name(), a.text()))
                .toList();
        String fileName = files.findById(budget.getFileId()).map(SourceFile::getOriginalName).orElse(null);
        // Active reallocations of this budget version (RF-03.1.7), reattached to the entries by the stable key
        List<BudgetVsActualCalculator.ReallocatedEntry> active = reallocations.findByBudgetIdAndUndoneAtIsNull(budget.getId())
                .stream().map(Reallocation::forCalculation).toList();
        return BudgetVsActualCalculator.calculate(new Input(budget, fileName, readChecks,
                mappings.findByBudgetIdOrderByAccountCode(budget.getId()), fundByLine, names,
                condominium.getOperatingFundId(), cashFlows, ofPeriod, active, limit, budgetWarnings, period));
    }

    /** The condominium's read cash flows with a period (trial balances completed or to review). */
    public List<CashFlowFile> cashFlows(UUID condominiumId) {
        return files.findByCondominiumIdAndCategoryAndStatusIn(condominiumId, FileCategory.BALANCETE, READ_CASH_FLOW)
                .stream().filter(a -> a.getPeriodStart() != null && a.getPeriodEnd() != null)
                .map(a -> new CashFlowFile(a.getId(), a.getOriginalName(), a.getSha256(), a.getPeriodStart(),
                        a.getPeriodEnd(), a.getUploadedAt(), a.getUploadedBy()))
                .sorted(Comparator.comparing(CashFlowFile::fileId)).toList();
    }

    private Optional<Budget> chooseBudget(UUID condominiumId, Period period, UUID budgetId) {
        if (budgetId != null) {
            return Optional.of(budgets.findByIdAndCondominiumId(budgetId, condominiumId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PO não encontrada")));
        }
        if (period instanceof Month m) {
            return budgetQuery.activeInMonth(condominiumId, m.month());
        }
        // Cumulative without a given budget: the most recent confirmed version
        return budgets.findByCondominiumIdAndStatusIn(condominiumId, EnumSet.of(BudgetStatus.CONFIRMADA)).stream()
                .filter(p -> p.getVersion() != null).max(Comparator.comparing(Budget::getVersion));
    }

    public static Period period(String text) {
        String t = text == null ? "" : text.trim();
        if (t.equalsIgnoreCase("acumulado")) {
            return new Cumulative();
        }
        try {
            return new Month(YearMonth.parse(t));
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Período deve ser AAAA-MM (ex.: 2026-09) ou \"acumulado\": " + text);
        }
    }
}
