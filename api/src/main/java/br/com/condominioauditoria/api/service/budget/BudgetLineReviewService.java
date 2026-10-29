package br.com.condominioauditoria.api.service.budget;

import br.com.condominioauditoria.api.dto.request.budget.BudgetLineOriginRequest;
import br.com.condominioauditoria.api.dto.request.budget.LineReviewBatchRequest;
import br.com.condominioauditoria.api.dto.request.budget.LineReviewItemRequest;
import br.com.condominioauditoria.api.dto.response.budget.BudgetLineReviewEventResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetLineReviewResponse;
import br.com.condominioauditoria.api.dto.response.budget.LineReviewLineResponse;
import br.com.condominioauditoria.api.dto.response.budget.LineReviewStateResponse;
import br.com.condominioauditoria.api.mapper.BudgetLineReviewMapper;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.budget.BudgetLineOrigin;
import br.com.condominioauditoria.api.model.budget.BudgetLineReviewEvent;
import br.com.condominioauditoria.api.model.enums.BudgetLineReviewAction;
import br.com.condominioauditoria.api.model.enums.BudgetLineReviewStatus;
import br.com.condominioauditoria.api.model.enums.BudgetLineType;
import br.com.condominioauditoria.api.model.enums.OriginSource;
import br.com.condominioauditoria.api.repository.budget.BudgetLineOriginRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetLineRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetLineReviewEventRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetRepository;
import br.com.condominioauditoria.api.service.calculator.BudgetLineReviewCheck;
import br.com.condominioauditoria.api.service.calculator.BudgetStructure;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Review of the budget lines by a person (RF-13.12; ADR 0009, Decision 1) and the origin of each line (RF-13.13,
 * Decision 3 B). The warnings come from the pure function {@link BudgetLineReviewCheck}, on each request. Every change
 * goes to the insert-only trail. {@link #state} is the only place that tells the screens which distribute spending by
 * line whether the lines are reviewed: while they are not, those screens show the pending step instead of per-line
 * numbers, with no 4xx (same pattern as the pending account mapping, PR #58).
 */
@Service
public class BudgetLineReviewService {

    private static final Logger log = LoggerFactory.getLogger(BudgetLineReviewService.class);

    private final BudgetRepository budgets;
    private final BudgetLineRepository lines;
    private final BudgetLineReviewEventRepository events;
    private final BudgetLineOriginRepository origins;

    public BudgetLineReviewService(BudgetRepository budgets, BudgetLineRepository lines,
            BudgetLineReviewEventRepository events, BudgetLineOriginRepository origins) {
        this.budgets = budgets;
        this.lines = lines;
        this.events = events;
        this.origins = origins;
    }

    /** Review state of a budget; null without a budget. */
    @Transactional(readOnly = true)
    public LineReviewStateResponse state(Budget budget) {
        return budget == null ? null : state(budget.getId(), lines.findByBudgetIdOrderByPosition(budget.getId()));
    }

    /**
     * Review state from the lines already loaded (expense lines and 1.9 fund lines; groups and total do not count).
     * Since Q54 (29/10/2026) the lines read are valid by themselves ("automática"): {@code reviewed} is always true and
     * there is no pending step; {@code reviewedLines} counts the lines a person confirmed (optional).
     */
    public static LineReviewStateResponse state(UUID budgetId, List<BudgetLine> budgetLines) {
        List<BudgetLine> reviewable = budgetLines.stream().filter(l -> l.getType() == BudgetLineType.LINE).toList();
        int reviewed = (int) reviewable.stream().filter(BudgetLine::isReviewed).count();
        return new LineReviewStateResponse(budgetId, true, reviewed, reviewable.size(), null);
    }

    @Transactional(readOnly = true)
    public BudgetLineReviewResponse list(UUID condominiumId, UUID budgetId) {
        Budget budget = budget(condominiumId, budgetId);
        return response(budget, lines.findByBudgetIdOrderByPosition(budget.getId()));
    }

    /**
     * The Admin reviews, reopens or marks lines, one by one or "conferir todas". Rejects with every reason at once.
     * Unchanged lines are skipped (no event).
     */
    @Transactional
    public BudgetLineReviewResponse review(UUID condominiumId, UUID budgetId, LineReviewBatchRequest request,
            String username) {
        Budget budget = budget(condominiumId, budgetId);
        List<BudgetLine> budgetLines = lines.findByBudgetIdOrderByPosition(budget.getId());
        Map<UUID, BudgetLine> byId = budgetLines.stream().filter(l -> l.getType() == BudgetLineType.LINE)
                .collect(Collectors.toMap(BudgetLine::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        List<LineReviewItemRequest> items = request == null || request.items() == null ? List.of() : request.items();
        boolean all = request != null && request.reviewAll();
        if (items.isEmpty() && !all) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Informe ao menos uma linha ou \"conferir todas\"");
        }
        List<String> reasons = new ArrayList<>();
        Map<UUID, LineReviewItemRequest> chosen = new LinkedHashMap<>();
        for (LineReviewItemRequest item : items) {
            BudgetLine l = item == null || item.lineId() == null ? null : byId.get(item.lineId());
            if (l == null) {
                reasons.add("Linha " + (item == null ? null : item.lineId()) + " não é linha desta PO.");
            } else if (chosen.put(l.getId(), item) != null) {
                reasons.add("Mais de uma conferência para a linha " + l.getEffectiveCode() + " (ordem "
                        + l.getPosition() + ").");
            }
        }
        if (!reasons.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, String.join(" ", reasons));
        }
        String reason = request.reason() == null || request.reason().isBlank() ? null : request.reason().trim();
        Instant now = Instant.now();
        int changed = 0;
        for (LineReviewItemRequest item : chosen.values()) {
            changed += apply(byId.get(item.lineId()), item.status(), item.groupAccount(), item.descriptionPrevails(),
                    username, now, reason);
        }
        if (all) {
            for (BudgetLine l : byId.values()) {
                if (!l.isReviewed() && !chosen.containsKey(l.getId())) {
                    changed += apply(l, BudgetLineReviewStatus.REVIEWED, null, null, username, now, reason);
                }
            }
        }
        lines.saveAll(budgetLines);
        log.info("Conferência das linhas da PO {}: {} mudança(s)", budget.getId(), changed);
        return response(budget, budgetLines);
    }

    private int apply(BudgetLine l, BudgetLineReviewStatus status, Boolean groupAccount, Boolean descriptionPrevails,
            String username, Instant now, String reason) {
        int changed = 0;
        BudgetLineReviewStatus before = l.getReviewStatus();
        boolean newGroup = groupAccount == null ? l.isGroupAccount() : groupAccount;
        boolean newDescription = descriptionPrevails == null ? l.isDescriptionPrevails() : descriptionPrevails;
        if (newGroup != l.isGroupAccount() || newDescription != l.isDescriptionPrevails()) {
            l.setMarks(newGroup, newDescription);
            events.save(new BudgetLineReviewEvent(l, BudgetLineReviewAction.MARKS_CHANGED, before, username, now,
                    reason));
            changed++;
        }
        if (status != null && status != before) {
            l.setReview(status, username, now);
            events.save(new BudgetLineReviewEvent(l, status == BudgetLineReviewStatus.REVIEWED
                    ? BudgetLineReviewAction.REVIEWED : BudgetLineReviewAction.REOPENED, before, username, now, reason));
            changed++;
        }
        return changed;
    }

    /**
     * The Admin fills in or corrects the origin of a line (RF-13.13): every field replaced, source ADMIN, with an
     * ORIGIN_CHANGED event. Never changes the planned amount (O4).
     */
    @Transactional
    public LineReviewLineResponse saveOrigin(UUID condominiumId, UUID budgetId, UUID lineId,
            BudgetLineOriginRequest request, String username) {
        Budget budget = budget(condominiumId, budgetId);
        List<BudgetLine> budgetLines = lines.findByBudgetIdOrderByPosition(budget.getId());
        BudgetLine line = budgetLines.stream()
                .filter(l -> l.getId().equals(lineId) && l.getType() == BudgetLineType.LINE).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Linha não encontrada nesta PO"));
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Informe a origem da linha");
        }
        List<String> reasons = new ArrayList<>();
        YearMonth from = month(request.averageFrom(), "averageFrom", reasons);
        YearMonth to = month(request.averageTo(), "averageTo", reasons);
        if (from != null && to != null && to.isBefore(from)) {
            reasons.add("O fim da média (" + to + ") é antes do início (" + from + ").");
        }
        List<String> accounts = request.sourceAccounts() == null ? List.of()
                : request.sourceAccounts().stream().filter(Objects::nonNull).map(String::trim)
                        .filter(s -> !s.isEmpty()).toList();
        accounts.stream().filter(a -> !a.matches("\\d{1,10}")).forEach(a -> reasons.add("Conta de origem \"" + a
                + "\" deve ser o código numérico da conta."));
        if (!reasons.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, String.join(" ", reasons));
        }
        Instant now = Instant.now();
        BudgetLineOrigin origin = origins.findByBudgetLineId(line.getId()).orElseGet(() -> new BudgetLineOrigin(line));
        origin.set(accounts, from, to, money(request.averageAmount()), money(request.baseAmount()), request.method(),
                text(request.indexName()), request.indexRate(), Boolean.TRUE.equals(request.indexForecast()),
                text(request.indexNote()), money(request.finalAmount()), text(request.reason()));
        origin.source(OriginSource.ADMIN, null, null, null, null, null, username, now);
        origins.save(origin);
        events.save(new BudgetLineReviewEvent(line, BudgetLineReviewAction.ORIGIN_CHANGED, line.getReviewStatus(),
                username, now, text(request.reason())));
        log.info("Origem da linha {} da PO {} gravada", line.getEffectiveCode(), budget.getId());
        BudgetLineReviewResponse all = response(budget, budgetLines);
        return all.lines().stream().filter(l -> l.lineId().equals(line.getId())).findFirst().orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<BudgetLineReviewEventResponse> events(UUID condominiumId, UUID budgetId) {
        return events.findByBudgetIdOrderByOccurredAtAscLineCodeAsc(budget(condominiumId, budgetId).getId()).stream()
                .map(BudgetLineReviewMapper::toResponse).toList();
    }

    /** Origins of the lines of a budget, by line id. */
    public Map<UUID, BudgetLineOrigin> origins(UUID budgetId) {
        Map<UUID, BudgetLineOrigin> m = new HashMap<>();
        origins.findByBudgetId(budgetId).forEach(o -> m.put(o.getBudgetLineId(), o));
        return m;
    }

    private BudgetLineReviewResponse response(Budget budget, List<BudgetLine> budgetLines) {
        BudgetStructure structure = BudgetStructure.of(budgetLines);
        Map<UUID, BudgetLineOrigin> byLine = origins(budget.getId());
        Map<UUID, List<BudgetLineReviewCheck.Warning>> warnings = BudgetLineReviewCheck.check(structure, byLine);
        List<LineReviewLineResponse> list = new ArrayList<>();
        for (BudgetStructure.Group g : structure.groups()) {
            g.lines().forEach(l -> list.add(BudgetLineReviewMapper.toResponse(l, g.line(), g.funds(),
                    warnings.get(l.getId()), byLine.get(l.getId()))));
        }
        structure.ungrouped().forEach(l -> list.add(BudgetLineReviewMapper.toResponse(l, null, false,
                warnings.get(l.getId()), byLine.get(l.getId()))));
        list.sort(java.util.Comparator.comparingInt(LineReviewLineResponse::position));
        int withWarnings = (int) list.stream().filter(l -> !l.warnings().isEmpty()).count();
        int withOrigin = (int) list.stream().filter(l -> l.origin() != null).count();
        return new BudgetLineReviewResponse(budget.getId(), budget.getVersion(), budget.getStatus(),
                BudgetLineReviewCheck.VERSION, state(budget.getId(), budgetLines), withWarnings, withOrigin,
                List.copyOf(list));
    }

    private static YearMonth month(String text, String field, List<String> reasons) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(text.trim());
        } catch (DateTimeParseException e) {
            reasons.add(field + " deve ser AAAA-MM: " + text + ".");
            return null;
        }
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static String text(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private Budget budget(UUID condominiumId, UUID budgetId) {
        return budgets.findByIdAndCondominiumId(budgetId, condominiumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PO não encontrada"));
    }
}
