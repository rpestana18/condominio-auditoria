package br.com.condominioauditoria.api.service.budget;

import br.com.condominioauditoria.api.dto.request.budget.SpendingProfileBatchRequest;
import br.com.condominioauditoria.api.dto.request.budget.SpendingProfileItemRequest;
import br.com.condominioauditoria.api.dto.response.budget.SpendingProfileEventResponse;
import br.com.condominioauditoria.api.dto.response.budget.SpendingProfileLineResponse;
import br.com.condominioauditoria.api.dto.response.budget.SpendingProfilesResponse;
import br.com.condominioauditoria.api.mapper.SpendingProfileMapper;
import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.model.budget.SpendingProfileEvent;
import br.com.condominioauditoria.api.model.enums.SpendingProfile;
import br.com.condominioauditoria.api.model.enums.SpendingProfileStatus;
import br.com.condominioauditoria.api.repository.budget.BudgetLineRepository;
import br.com.condominioauditoria.api.repository.budget.BudgetRepository;
import br.com.condominioauditoria.api.repository.budget.SpendingProfileEventRepository;
import br.com.condominioauditoria.api.service.calculator.BudgetStructure;
import br.com.condominioauditoria.api.service.calculator.BudgetTrackingGrid.LineProfile;
import br.com.condominioauditoria.api.service.calculator.SpendingProfileSuggestion;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Spending profile of each expense line (RF-13.3; ADR 0007, Decision 2): suggested by {@link SpendingProfileSuggestion}
 * when the budget is confirmed, confirmed by the Admin, every change in the trail. The profile never changes the
 * planned amount: it only classifies the line's balance in the balance bridge and the highlight of the tracking grid.
 */
@Service
public class SpendingProfileService {

    private static final Logger log = LoggerFactory.getLogger(SpendingProfileService.class);

    private final BudgetRepository budgets;
    private final BudgetLineRepository lines;
    private final SpendingProfileEventRepository events;

    public SpendingProfileService(BudgetRepository budgets, BudgetLineRepository lines,
            SpendingProfileEventRepository events) {
        this.budgets = budgets;
        this.lines = lines;
        this.events = events;
    }

    /**
     * Stores the suggestion of every expense line without a confirmed profile, with a SUGGESTED event. Called inside
     * the budget confirmation transaction; the caller saves the lines.
     */
    public void suggestOnConfirmation(Budget budget, BudgetStructure structure, String username, Instant now) {
        for (BudgetLine l : AccountMappingService.debitTargets(structure)) {
            if (l.getSpendingProfileStatus() == SpendingProfileStatus.CONFIRMED) {
                continue;
            }
            SpendingProfile previous = l.getSpendingProfile();
            SpendingProfileStatus previousStatus = l.getSpendingProfileStatus();
            var s = SpendingProfileSuggestion.suggest(l);
            l.setSpendingProfile(s.profile(), SpendingProfileStatus.SUGGESTED, s.reason(), username, now);
            events.save(new SpendingProfileEvent(l, previous, previousStatus, username, now));
        }
        log.info("Tipos das linhas da PO {} sugeridos", budget.getId());
    }

    @Transactional(readOnly = true)
    public SpendingProfilesResponse list(UUID condominiumId, UUID budgetId) {
        Budget budget = budget(condominiumId, budgetId);
        return response(budget, lines.findByBudgetIdOrderByPosition(budget.getId()));
    }

    /** Profile in use of each expense line (only CONFIRMED counts; MONTHLY otherwise). */
    public Map<UUID, LineProfile> profiles(List<BudgetLine> budgetLines) {
        Map<UUID, LineProfile> m = new HashMap<>();
        AccountMappingService.debitTargets(BudgetStructure.of(budgetLines))
                .forEach(l -> m.put(l.getId(), SpendingProfileMapper.profile(l)));
        return m;
    }

    /**
     * The Admin confirms the profile of the given lines (null profile = the current suggestion). Only after the budget
     * confirmation, only expense lines (1.1 to 1.8). Rejects with every reason at once. Unchanged lines are skipped.
     */
    @Transactional
    public SpendingProfilesResponse confirm(UUID condominiumId, UUID budgetId, SpendingProfileBatchRequest request,
            String username) {
        Budget budget = budget(condominiumId, budgetId);
        if (!budget.getStatus().isLocked()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Confirme a PO antes de confirmar o tipo das linhas");
        }
        List<BudgetLine> budgetLines = lines.findByBudgetIdOrderByPosition(budget.getId());
        Map<UUID, BudgetLine> targets = new LinkedHashMap<>();
        AccountMappingService.debitTargets(BudgetStructure.of(budgetLines)).forEach(l -> targets.put(l.getId(), l));
        List<SpendingProfileItemRequest> items = request == null || request.items() == null ? List.of()
                : request.items();
        if (items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Informe ao menos uma linha");
        }
        List<String> reasons = new ArrayList<>();
        Map<BudgetLine, SpendingProfile> chosen = new LinkedHashMap<>();
        for (SpendingProfileItemRequest item : items) {
            BudgetLine l = item == null || item.lineId() == null ? null : targets.get(item.lineId());
            if (l == null) {
                reasons.add("Linha " + (item == null ? null : item.lineId()) + " não é linha de despesa desta PO"
                        + " (grupos 1.1 a 1.8).");
            } else if (chosen.put(l, item.profile() == null ? SpendingProfileSuggestion.suggest(l).profile()
                    : item.profile()) != null) {
                reasons.add("Mais de um tipo para a linha " + l.getEffectiveCode() + " (ordem " + l.getPosition()
                        + ").");
            }
        }
        if (!reasons.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, String.join(" ", reasons));
        }
        String reason = request.reason() == null || request.reason().isBlank() ? null : request.reason().trim();
        Instant now = Instant.now();
        int changed = 0;
        for (var e : chosen.entrySet()) {
            BudgetLine l = e.getKey();
            if (l.getSpendingProfileStatus() == SpendingProfileStatus.CONFIRMED
                    && l.getSpendingProfile() == e.getValue() && Objects.equals(l.getSpendingProfileReason(), reason)) {
                continue;
            }
            SpendingProfile previous = l.getSpendingProfile();
            SpendingProfileStatus previousStatus = l.getSpendingProfileStatus();
            l.setSpendingProfile(e.getValue(), SpendingProfileStatus.CONFIRMED, reason, username, now);
            events.save(new SpendingProfileEvent(l, previous, previousStatus, username, now));
            changed++;
        }
        lines.saveAll(budgetLines);
        log.info("Tipos das linhas da PO {}: {} confirmado(s) por {}", budget.getId(), changed, username);
        return response(budget, budgetLines);
    }

    @Transactional(readOnly = true)
    public List<SpendingProfileEventResponse> events(UUID condominiumId, UUID budgetId) {
        return events.findByBudgetIdOrderByOccurredAtAscLineCodeAsc(budget(condominiumId, budgetId).getId()).stream()
                .map(SpendingProfileMapper::toResponse).toList();
    }

    private SpendingProfilesResponse response(Budget budget, List<BudgetLine> budgetLines) {
        List<SpendingProfileLineResponse> list = new ArrayList<>();
        for (BudgetStructure.Group g : BudgetStructure.of(budgetLines).groupsWithoutFunds()) {
            g.lines().forEach(l -> list.add(SpendingProfileMapper.toResponse(l, g.line().getPrintedCode())));
        }
        int confirmed = (int) list.stream().filter(l -> l.status() == SpendingProfileStatus.CONFIRMED).count();
        return new SpendingProfilesResponse(budget.getId(), budget.getVersion(), confirmed, list.size() - confirmed,
                List.copyOf(list));
    }

    private Budget budget(UUID condominiumId, UUID budgetId) {
        return budgets.findByIdAndCondominiumId(budgetId, condominiumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PO não encontrada"));
    }
}
