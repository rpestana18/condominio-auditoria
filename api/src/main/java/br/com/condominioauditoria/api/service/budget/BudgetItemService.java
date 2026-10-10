package br.com.condominioauditoria.api.service.budget;

import br.com.condominioauditoria.api.dto.request.budget.BudgetItemBatchRequest;
import br.com.condominioauditoria.api.dto.request.budget.LineBudgetItemRequest;
import br.com.condominioauditoria.api.dto.request.budget.NewBudgetItemRequest;
import br.com.condominioauditoria.api.dto.request.budget.RenameBudgetItemRequest;
import br.com.condominioauditoria.api.dto.response.budget.BudgetItemBatchResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetItemEventResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetItemResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetItemSuggestionsResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetItemSummaryResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetItemsResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetLineItemResponse;
import br.com.condominioauditoria.api.dto.response.budget.LineWithoutSuggestionResponse;
import br.com.condominioauditoria.api.dto.response.budget.SkippedLineResponse;
import br.com.condominioauditoria.api.mapper.BudgetItemMapper;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetItem;
import br.com.condominioauditoria.api.model.budget.BudgetItemEvent;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.budget.BudgetLineItem;
import br.com.condominioauditoria.api.model.enums.BudgetItemAction;
import br.com.condominioauditoria.api.model.enums.BudgetItemBatchAction;
import br.com.condominioauditoria.api.model.enums.BudgetItemFilter;
import br.com.condominioauditoria.api.model.enums.BudgetItemSource;
import br.com.condominioauditoria.api.model.enums.BudgetItemStatus;
import br.com.condominioauditoria.api.model.enums.BudgetStatus;
import br.com.condominioauditoria.api.repository.budget.BudgetItemEventRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetItemRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetLineItemRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetLineRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetRepository;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import br.com.condominioauditoria.api.service.calculator.BudgetItemSuggestion;
import br.com.condominioauditoria.api.service.calculator.BudgetItemSuggestion.Confirmed;
import br.com.condominioauditoria.api.service.calculator.BudgetItemSuggestion.LineWithGroup;
import br.com.condominioauditoria.api.service.calculator.BudgetStructure;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * The condominium's budget item catalog and the item of each line of the confirmed budgets (RF-11.7; ADR 0005, Decision
 * 1). The first confirmed budget creates one item per line, already confirmed. In the others, the suggestion ({@link
 * BudgetItemSuggestion}) always comes in as SUGGESTED and only counts in the comparison after the Admin confirms it.
 * Every change writes an event to the trail (insert-only). Items do not change any fiscal year's budget vs. actual: no
 * recalculation is triggered.
 */
@Service
public class BudgetItemService {

    private static final Logger log = LoggerFactory.getLogger(BudgetItemService.class);
    private static final int NAME_MAX_LENGTH = 300;

    private final CondominiumRepository condominiums;
    private final BudgetRepository budgets;
    private final BudgetLineRepository lines;
    private final BudgetItemRepository items;
    private final BudgetLineItemRepository lineItems;
    private final BudgetItemEventRepository events;

    public BudgetItemService(CondominiumRepository condominiums, BudgetRepository budgets, BudgetLineRepository lines,
            BudgetItemRepository items, BudgetLineItemRepository lineItems, BudgetItemEventRepository events) {
        this.condominiums = condominiums;
        this.budgets = budgets;
        this.lines = lines;
        this.items = items;
        this.lineItems = lineItems;
        this.events = events;
    }

    /** Budget, lines that receive an item and the items already linked, loaded once per request. */
    private record Context(Budget budget, List<LineWithGroup> lines, Map<UUID, LineWithGroup> byId,
            Map<UUID, BudgetLineItem> linked) {
    }

    @Transactional(readOnly = true)
    public List<BudgetItemResponse> catalog(UUID condominiumId) {
        return items.findByCondominiumIdOrderByName(condominiumId).stream().map(BudgetItemMapper::toResponse).toList();
    }

    @Transactional
    public BudgetItemResponse create(UUID condominiumId, NewBudgetItemRequest request, String username) {
        String name = validateName(request == null ? null : request.name());
        String group = request.group() == null || request.group().isBlank() ? null : request.group().trim();
        BudgetItem r = new BudgetItem(condominiumId, name, group, null, username, Instant.now());
        items.save(r);
        events.save(BudgetItemEvent.created(r, null, username, r.getCreatedAt()));
        log.info("Rubrica {} \"{}\" criada no condomínio {} por {}", r.getId(), name, condominiumId, username);
        return BudgetItemMapper.toResponse(r);
    }

    @Transactional
    public BudgetItemResponse rename(UUID condominiumId, UUID itemId, RenameBudgetItemRequest request,
            String username) {
        BudgetItem r = item(condominiumId, itemId);
        String name = validateName(request == null ? null : request.name());
        if (!name.equals(r.getName())) {
            String before = r.getName();
            r.rename(name);
            items.save(r);
            events.save(BudgetItemEvent.renamed(r, before, username, Instant.now()));
        }
        return BudgetItemMapper.toResponse(r);
    }

    @Transactional(readOnly = true)
    public BudgetItemsResponse list(UUID condominiumId, UUID budgetId, BudgetItemFilter filter) {
        Context ctx = context(budget(condominiumId, budgetId));
        Map<UUID, BudgetItem> catalog = items.findByCondominiumIdOrderByName(condominiumId).stream()
                .collect(Collectors.toMap(BudgetItem::getId, Function.identity()));
        List<BudgetLineItemResponse> all = ctx.lines().stream()
                .map(l -> lineWithItem(l, ctx.linked().get(l.line().getId()), catalog)).toList();
        BudgetItemSummaryResponse summary = new BudgetItemSummaryResponse(all.size(), count(all,
                BudgetItemStatus.CONFIRMED),
                count(all, BudgetItemStatus.SUGGESTED), count(all, BudgetItemStatus.REJECTED),
                (int) all.stream().filter(l -> l.status() == null).count());
        BudgetItemFilter f = filter == null ? BudgetItemFilter.ALL : filter;
        return new BudgetItemsResponse(ctx.budget().getId(), ctx.budget().getVersion(), summary,
                all.stream().filter(l -> matches(l, f)).toList());
    }

    /**
     * Generates the budget's items: in the condominium's first confirmed budget, one item per line, already confirmed;
     * in the others, suggestions for the lines still without item. Never touches a line that already has an item.
     */
    @Transactional
    public BudgetItemSuggestionsResponse suggest(UUID condominiumId, UUID budgetId, String username) {
        condominiums.lockById(condominiumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condomínio não encontrado"));
        return generate(writeContext(condominiumId, budgetId).budget(), username, Instant.now());
    }

    /**
     * Called on budget confirmation, in the same transaction (the condominium is already locked). The budget must be
     * confirmed.
     */
    public BudgetItemSuggestionsResponse onConfirm(Budget budget, String username, Instant now) {
        return generate(budget, username, now);
    }

    private BudgetItemSuggestionsResponse generate(Budget budget, String username, Instant now) {
        Context ctx = context(budget);
        if (!items.existsByCondominiumId(budget.getCondominiumId())) {
            for (LineWithGroup l : ctx.lines()) {
                BudgetItem r = new BudgetItem(budget.getCondominiumId(), nameOf(l), l.group(), l.line().getId(),
                        username, now);
                items.save(r);
                link(l.line(), null, r, BudgetItemStatus.CONFIRMED, BudgetItemSource.FIRST_BUDGET,
                        "primeira PO confirmada do condomínio: a linha vira rubrica", username, now);
            }
            log.info("Rubricas: primeira PO {} do condomínio {}, {} rubricas criadas", budget.getId(),
                    budget.getCondominiumId(),
                    ctx.lines().size());
            return new BudgetItemSuggestionsResponse(true, ctx.lines().size(), 0, 0, 0, List.of());
        }

        List<LineWithGroup> newLines = ctx.lines().stream().filter(l -> !ctx.linked().containsKey(l.line().getId()))
                .toList();
        Optional<Budget> previous = previousVersion(budget);
        List<Confirmed> fromPrevious = new ArrayList<>();
        List<Confirmed> fromOthers = new ArrayList<>();
        Map<UUID, List<BudgetLineItem>> confirmedByBudget = lineItems
                .findByCondominiumIdAndStatus(budget.getCondominiumId(), BudgetItemStatus.CONFIRMED).stream()
                .filter(lineItem -> !lineItem.getBudgetId().equals(budget.getId()))
                .collect(Collectors.groupingBy(BudgetLineItem::getBudgetId, LinkedHashMap::new, Collectors.toList()));
        confirmedByBudget.forEach((budgetId, linked) -> {
            Map<UUID, LineWithGroup> ofBudget = BudgetItemSuggestion.lines(BudgetStructure.of(
                    lines.findByBudgetIdOrderByPosition(budgetId))).stream()
                    .collect(Collectors.toMap(l -> l.line().getId(), Function.identity()));
            for (BudgetLineItem lineItem : linked) {
                LineWithGroup l = ofBudget.get(lineItem.getBudgetLineId());
                if (l != null) {
                    Confirmed c = new Confirmed(l, lineItem.getBudgetItemId());
                    fromOthers.add(c);
                    if (previous.isPresent() && previous.get().getId().equals(budgetId)) {
                        fromPrevious.add(c);
                    }
                }
            }
        });

        Map<UUID, BudgetItem> catalog = items.findByCondominiumIdOrderByName(budget.getCondominiumId()).stream()
                .collect(Collectors.toMap(BudgetItem::getId, Function.identity()));
        int previousVersion = 0;
        int byAccount = 0;
        List<LineWithoutSuggestionResponse> withoutSuggestion = new ArrayList<>();
        var results = BudgetItemSuggestion.suggest(newLines, ctx.lines(), fromPrevious, fromOthers);
        for (LineWithGroup l : newLines) {
            var r = results.get(l.line().getId());
            if (r instanceof BudgetItemSuggestion.Suggested s && catalog.containsKey(s.budgetItemId())) {
                link(l.line(), null, catalog.get(s.budgetItemId()), BudgetItemStatus.SUGGESTED, s.source(), s.reason(),
                        username, now);
                if (s.source() == BudgetItemSource.PREVIOUS_VERSION) {
                    previousVersion++;
                } else {
                    byAccount++;
                }
            } else {
                withoutSuggestion.add(new LineWithoutSuggestionResponse(l.line().getId(), l.line().getEffectiveCode(),
                        l.label(),
                        r.reason()));
            }
        }
        log.info("Rubricas da PO {}: {} da versão anterior, {} pela conta, {} sem sugestão", budget.getId(),
                previousVersion,
                byAccount, withoutSuggestion.size());
        return new BudgetItemSuggestionsResponse(false, 0, previousVersion + byAccount, previousVersion, byAccount,
                withoutSuggestion);
    }

    /** The Admin chooses a line's item: one from the catalog or a new one created from the line. */
    @Transactional
    public BudgetLineItemResponse setItem(UUID condominiumId, UUID budgetId, UUID lineId, LineBudgetItemRequest request,
            String username) {
        Context ctx = writeContext(condominiumId, budgetId);
        LineWithGroup l = ctx.byId().get(lineId);
        if (l == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "A linha informada não é uma linha de despesa ou de fundo desta PO");
        }
        boolean existing = request != null && request.budgetItemId() != null;
        boolean createNew = request != null && request.newBudgetItem() != null && !request.newBudgetItem().isBlank();
        if (existing == createNew) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Informe a rubrica do catálogo ou o nome de uma rubrica nova, não os dois");
        }
        Instant now = Instant.now();
        BudgetItem r;
        if (createNew) {
            r = new BudgetItem(condominiumId, validateName(request.newBudgetItem()), l.group(), l.line().getId(),
                    username,
                    now);
            items.save(r);
            events.save(BudgetItemEvent.created(r, l.line(), username, now));
        } else {
            r = item(condominiumId, request.budgetItemId());
        }
        BudgetItemStatus status = request.confirm() == null || request.confirm() ? BudgetItemStatus.CONFIRMED
                : BudgetItemStatus.SUGGESTED;
        BudgetLineItem lineItem = link(l.line(), ctx.linked().get(lineId), r, status, BudgetItemSource.MANUAL,
                "escolhida pelo Admin", username, now);
        return lineWithItem(l, lineItem, Map.of(r.getId(), r));
    }

    /** Confirm or reject in batch, one event per changed line. */
    @Transactional
    public BudgetItemBatchResponse batch(UUID condominiumId, UUID budgetId, BudgetItemBatchRequest request,
            String username) {
        Context ctx = writeContext(condominiumId, budgetId);
        if (request == null || request.action() == null || request.lines() == null || request.lines().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Informe a ação e as linhas");
        }
        BudgetItemStatus newStatus = request.action() == BudgetItemBatchAction.CONFIRM ? BudgetItemStatus.CONFIRMED
                : BudgetItemStatus.REJECTED;
        Map<UUID, BudgetItem> catalog = items.findByCondominiumIdOrderByName(condominiumId).stream()
                .collect(Collectors.toMap(BudgetItem::getId, Function.identity()));
        Instant now = Instant.now();
        int changed = 0;
        List<SkippedLineResponse> skipped = new ArrayList<>();
        for (UUID lineId : request.lines().stream().distinct().toList()) {
            LineWithGroup l = ctx.byId().get(lineId);
            BudgetLineItem lineItem = ctx.linked().get(lineId);
            if (l == null) {
                skipped.add(new SkippedLineResponse(lineId, "não é uma linha de despesa ou de fundo desta PO"));
            } else if (lineItem == null) {
                skipped.add(new SkippedLineResponse(lineId, "linha sem rubrica: escolha a rubrica"));
            } else if (lineItem.getStatus() == newStatus) {
                skipped.add(new SkippedLineResponse(lineId, "já está " + newStatus.label()));
            } else {
                BudgetItem r = catalog.get(lineItem.getBudgetItemId());
                BudgetItemStatus before = lineItem.getStatus();
                lineItem.changeStatus(newStatus, username, now);
                lineItems.save(lineItem);
                events.save(BudgetItemEvent.forLine(l.line(), lineItem, actionForStatus(newStatus), r, before, r,
                        username, now));
                changed++;
            }
        }
        log.info("Rubricas da PO {}: {} linha(s) {} por {}", ctx.budget().getId(), changed, newStatus, username);
        return new BudgetItemBatchResponse(changed, skipped);
    }

    @Transactional(readOnly = true)
    public List<BudgetItemEventResponse> events(UUID condominiumId, UUID budgetId) {
        return events.findByBudgetIdOrderByOccurredAtAscLineCodeAsc(budget(condominiumId, budgetId).getId()).stream()
                .map(BudgetItemMapper::toResponse).toList();
    }

    /** Links (or changes) a line's item and saves the event. Without a change of item or status, saves nothing. */
    private BudgetLineItem link(BudgetLine line, BudgetLineItem current, BudgetItem item, BudgetItemStatus status,
            BudgetItemSource source, String reason, String username, Instant now) {
        if (current == null) {
            BudgetLineItem lineItem = new BudgetLineItem(line, item, status, source, reason, username, now);
            lineItems.save(lineItem);
            events.save(BudgetItemEvent.forLine(line, lineItem, actionForStatus(status), null, null, item, username,
                    now));
            return lineItem;
        }
        boolean itemChanged = !current.getBudgetItemId().equals(item.getId());
        if (!itemChanged && current.getStatus() == status) {
            return current;
        }
        BudgetItem before = items.findById(current.getBudgetItemId()).orElse(null);
        BudgetItemStatus statusBefore = current.getStatus();
        current.change(item, status, source, reason, username, now);
        lineItems.save(current);
        events.save(BudgetItemEvent.forLine(line, current,
                itemChanged ? BudgetItemAction.CHANGED : actionForStatus(status),
                before, statusBefore, item, username, now));
        return current;
    }

    private static BudgetItemAction actionForStatus(BudgetItemStatus status) {
        return switch (status) {
            case SUGGESTED -> BudgetItemAction.SUGGESTED;
            case CONFIRMED -> BudgetItemAction.CONFIRMED;
            case REJECTED -> BudgetItemAction.REJECTED;
        };
    }

    /** Name of the item created from a line: the budget's account (or the account text), otherwise the description. */
    private static String nameOf(LineWithGroup l) {
        String name = l.label().isBlank() ? l.line().getEffectiveCode() : l.label();
        return name.length() > NAME_MAX_LENGTH ? name.substring(0, NAME_MAX_LENGTH) : name;
    }

    private Budget budget(UUID condominiumId, UUID budgetId) {
        return budgets.findByIdAndCondominiumId(budgetId, condominiumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PO não encontrada"));
    }

    private BudgetItem item(UUID condominiumId, UUID itemId) {
        return items.findByIdAndCondominiumId(itemId, condominiumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "A rubrica informada não é deste condomínio"));
    }

    private Context writeContext(UUID condominiumId, UUID budgetId) {
        Budget budget = budget(condominiumId, budgetId);
        if (!budget.getStatus().isLocked()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Confirme a PO antes das rubricas: as rubricas são das linhas de PO confirmada");
        }
        return context(budget);
    }

    private Context context(Budget budget) {
        List<LineWithGroup> ofBudget = BudgetItemSuggestion.lines(BudgetStructure.of(lines.findByBudgetIdOrderByPosition(budget.getId())));
        Map<UUID, LineWithGroup> byId = new LinkedHashMap<>();
        ofBudget.forEach(l -> byId.put(l.line().getId(), l));
        Map<UUID, BudgetLineItem> linked = lineItems.findByBudgetId(budget.getId()).stream()
                .collect(Collectors.toMap(BudgetLineItem::getBudgetLineId, Function.identity()));
        return new Context(budget, ofBudget, byId, linked);
    }

    /**
     * Confirmed version right before, in the same fiscal year (the one this budget superseded in a reapproval): highest
     * version lower than this one, with a fiscal year overlapping this one.
     */
    private Optional<Budget> previousVersion(Budget budget) {
        if (budget.getVersion() == null || budget.getFiscalYearStart() == null) {
            return Optional.empty();
        }
        return budgets.findByCondominiumIdAndStatusIn(budget.getCondominiumId(),
                        EnumSet.of(BudgetStatus.CONFIRMED, BudgetStatus.SUPERSEDED)).stream()
                .filter(p -> !p.getId().equals(budget.getId()) && p.getVersion() != null && p.getVersion() < budget.getVersion())
                .filter(p -> p.getFiscalYearStart() != null && !p.getFiscalYearStart().isAfter(budget.getFiscalYearEnd())
                        && !p.getFiscalYearEnd().isBefore(budget.getFiscalYearStart()))
                .max(Comparator.comparing(Budget::getVersion));
    }

    private static BudgetLineItemResponse lineWithItem(LineWithGroup l, BudgetLineItem lineItem, Map<UUID,
            BudgetItem> catalog) {
        BudgetLine line = l.line();
        if (lineItem == null) {
            return new BudgetLineItemResponse(line.getId(), line.getEffectiveCode(), l.group(), l.label(),
                    line.getDescription(), line.getBudgeted(), null, null, null, null, null, null);
        }
        BudgetItem r = catalog.get(lineItem.getBudgetItemId());
        return new BudgetLineItemResponse(line.getId(), line.getEffectiveCode(), l.group(), l.label(),
                line.getDescription(),
                line.getBudgeted(), r == null ? null : BudgetItemMapper.toResponse(r), lineItem.getStatus(),
                        lineItem.getSource(), lineItem.getReason(),
                lineItem.getUpdatedBy(), lineItem.getUpdatedAt());
    }

    private static boolean matches(BudgetLineItemResponse l, BudgetItemFilter f) {
        return switch (f) {
            case ALL -> true;
            case PENDING -> l.status() != BudgetItemStatus.CONFIRMED;
            case SUGGESTED -> l.status() == BudgetItemStatus.SUGGESTED;
            case CONFIRMED -> l.status() == BudgetItemStatus.CONFIRMED;
            case REJECTED -> l.status() == BudgetItemStatus.REJECTED;
            case NO_ITEM -> l.status() == null;
        };
    }

    private static int count(List<BudgetLineItemResponse> lines, BudgetItemStatus status) {
        return (int) lines.stream().filter(l -> l.status() == status).count();
    }

    private static String validateName(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Informe o nome da rubrica");
        }
        if (n.length() > NAME_MAX_LENGTH) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "O nome da rubrica passa de " + NAME_MAX_LENGTH + " caracteres");
        }
        return n;
    }
}
