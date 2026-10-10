package br.com.condominioauditoria.api.service.budget;

import br.com.condominioauditoria.api.dto.response.budget.BudgetVsActualResponse;
import br.com.condominioauditoria.api.dto.response.budget.FiscalYearComparisonResponse;
import br.com.condominioauditoria.api.model.audit.Finding;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetFundLink;
import br.com.condominioauditoria.api.model.budget.BudgetItem;
import br.com.condominioauditoria.api.model.budget.BudgetLineItem;
import br.com.condominioauditoria.api.model.condominium.Condominium;
import br.com.condominioauditoria.api.model.enums.BudgetItemStatus;
import br.com.condominioauditoria.api.model.enums.BudgetStatus;
import br.com.condominioauditoria.api.model.enums.BudgetVsActualStatus;
import br.com.condominioauditoria.api.model.enums.FindingStatus;
import br.com.condominioauditoria.api.model.enums.FiscalYearType;
import br.com.condominioauditoria.api.model.enums.MonthStatus;
import br.com.condominioauditoria.api.repository.audit.FindingRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetFundLinkRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetItemRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetLineItemRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetLineRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetRepository;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import br.com.condominioauditoria.api.service.calculator.FiscalYearComparison;
import br.com.condominioauditoria.api.service.calculator.FiscalYearComparison.Filter;
import br.com.condominioauditoria.api.service.calculator.FiscalYearComparison.Input;
import br.com.condominioauditoria.api.service.calculator.FiscalYearComparison.LineItem;
import br.com.condominioauditoria.api.service.calculator.PrintedColumn;
import java.time.Month;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Builds the {@link FiscalYearComparison} input from the database (RF-11.6; ADR 0005, Decision 2): one cumulative
 * calculation per fiscal year with a budget, through the same query as the budget vs. actual screen, and, in "mesmos
 * meses", one calculation per compared month. Nothing is stored.
 */
@Service
public class FiscalYearComparisonService {

    private final CondominiumRepository condominiums;
    private final BudgetRepository budgets;
    private final BudgetLineRepository lines;
    private final BudgetFundLinkRepository fundLinks;
    private final BudgetItemRepository budgetItems;
    private final BudgetLineItemRepository lineItems;
    private final FindingRepository findings;
    private final BudgetVsActualQueryService budgetVsActual;
    private final FiscalYearService fiscalYears;

    public FiscalYearComparisonService(CondominiumRepository condominiums, BudgetRepository budgets,
            BudgetLineRepository lines, BudgetFundLinkRepository fundLinks, BudgetItemRepository items,
            BudgetLineItemRepository lineItems, FindingRepository findings,
            BudgetVsActualQueryService budgetVsActual, FiscalYearService fiscalYears) {
        this.condominiums = condominiums;
        this.budgets = budgets;
        this.lines = lines;
        this.fundLinks = fundLinks;
        this.budgetItems = items;
        this.lineItems = lineItems;
        this.findings = findings;
        this.budgetVsActual = budgetVsActual;
        this.fiscalYears = fiscalYears;
    }

    /** A chosen fiscal year: the budget and whether it is its printed column. */
    private record Chosen(String id, Budget budget, boolean column) {

        public YearMonth start() {
            return column ? budget.getFiscalYearStart().minusMonths(12) : budget.getFiscalYearStart();
        }

        public YearMonth end() {
            return column ? budget.getFiscalYearStart().minusMonths(1) : budget.getFiscalYearEnd();
        }
    }

    /**
     * {@code ids}: "budget:&lt;uuid&gt;" (or just the uuid) and "column:&lt;uuid&gt;"; empty = the two most recent fiscal
     * years. Null {@code fundId} = all.
     */
    @Transactional(readOnly = true)
    public FiscalYearComparisonResponse compare(UUID condominiumId, List<String> ids, UUID fundId, boolean sameMonths) {
        Condominium condominium = condominiums.findById(condominiumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condomínio não encontrado"));
        if (fundId != null) {
            budgetVsActual.filterFund(condominiumId, fundId);
        }
        List<String> requests = ids == null ? List.of() : ids.stream().map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new)).stream().toList();
        if (requests.isEmpty()) {
            requests = fiscalYears.ids(condominiumId).stream().limit(2).toList();
        }
        List<Chosen> chosen = requests.stream().map(id -> choose(condominiumId, id))
                .sorted(Comparator.comparing(Chosen::start).reversed()).toList();
        if (chosen.size() < 2) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Escolha dois ou mais exercícios para comparar");
        }

        Map<String, BudgetVsActualResponse> cumulatives = new java.util.HashMap<>();
        for (Chosen x : chosen) {
            if (!x.column()) {
                cumulatives.put(x.id(), budgetVsActual.calculate(condominiumId, "acumulado",
                        x.budget().getId()).result());
            }
        }
        Set<Month> common = sameMonths ? FiscalYearComparison.sameMonths(cumulatives.values()) : Set.of();
        List<Finding> allFindings = findings.findByCondominiumIdOrderByReferenceMonthDescCreatedAtAsc(condominiumId);
        Map<UUID, LineItem> catalog = budgetItems.findByCondominiumIdOrderByName(condominiumId).stream()
                .collect(Collectors.toMap(BudgetItem::getId,
                        r -> new LineItem(r.getId(), r.getName(), r.getGroupCode())));

        List<Input> inputs = new ArrayList<>();
        for (Chosen x : chosen) {
            UUID budgetId = x.budget().getId();
            BudgetVsActualResponse cumulative = cumulatives.get(x.id());
            List<BudgetVsActualResponse> period = new ArrayList<>();
            List<String> months = new ArrayList<>();
            if (cumulative != null && sameMonths) {
                cumulative.months().stream()
                        .filter(m -> !m.extended() && m.status() == MonthStatus.WITH_CASH_FLOW
                                && common.contains(YearMonth.parse(m.month()).getMonth()))
                        .forEach(m -> {
                            BudgetVsActualResponse r = budgetVsActual.calculate(condominiumId, m.month(),
                                    budgetId).result();
                            if (r.status() == BudgetVsActualStatus.CALCULATED) {
                                period.add(r);
                                months.add(m.month());
                            }
                        });
            } else if (cumulative != null && cumulative.status() == BudgetVsActualStatus.CALCULATED) {
                period.add(cumulative);
                months.addAll(cumulative.summedMonths());
            }
            Map<UUID, LineItem> ofLine = lineItems.findByBudgetId(budgetId).stream()
                    .filter(l -> l.getStatus() == BudgetItemStatus.CONFIRMED && catalog.containsKey(l.getBudgetItemId()))
                    .collect(Collectors.toMap(BudgetLineItem::getBudgetLineId, l -> catalog.get(l.getBudgetItemId())));
            Integer open = x.column() ? null : (int) allFindings.stream()
                    .filter(a -> a.getStatus() == FindingStatus.OPEN && !a.getReferenceMonth().isBefore(x.start())
                            && !a.getReferenceMonth().isAfter(x.end())).count();
            String label = x.column() ? PrintedColumn.of(x.budget(), lines.findByBudgetIdOrderByPosition(budgetId))
                    .map(PrintedColumn::label).orElseThrow()
                    : FiscalYearService.label(x.start(), x.end());
            inputs.add(new Input(x.id(), x.column() ? FiscalYearType.PRINTED_COLUMN : FiscalYearType.PO, label,
                    budgetId, x.budget().getVersion(), x.start(), x.end(),
                            lines.findByBudgetIdOrderByPosition(budgetId),
                    fundLinks.findByBudgetId(budgetId).stream()
                            .collect(Collectors.toMap(BudgetFundLink::getBudgetLineId, BudgetFundLink::getFundId)),
                    ofLine, cumulative, List.copyOf(period), List.copyOf(months), open));
        }
        return FiscalYearComparison.compare(inputs, new Filter(fundId, condominium.getOperatingFundId(),
                sameMonths, sameMonths ? FiscalYearComparison.comparing(common) : null));
    }

    private Chosen choose(UUID condominiumId, String id) {
        boolean column = id.startsWith("column:");
        String text = id.startsWith("budget:") ? id.substring("budget:".length())
                : column ? id.substring("column:".length()) : id;
        UUID budgetId;
        try {
            budgetId = UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Exercício deve ser po:<id> ou coluna:<id>: " + id);
        }
        Budget budget = budgets.findByIdAndCondominiumId(budgetId, condominiumId)
                .filter(p -> p.getStatus() == BudgetStatus.CONFIRMED && p.getFiscalYearStart() != null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Exercício não encontrado (PO confirmada): " + id));
        if (column && PrintedColumn.of(budget, lines.findByBudgetIdOrderByPosition(budget.getId())).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "A PO não tem a coluna \"Orçado anterior\": " + id);
        }
        return new Chosen((column ? "column:" : "budget:") + budget.getId(), budget, column);
    }
}
