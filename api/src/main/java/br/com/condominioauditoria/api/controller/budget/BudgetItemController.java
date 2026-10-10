package br.com.condominioauditoria.api.controller.budget;

import br.com.condominioauditoria.api.dto.request.budget.BudgetItemBatchRequest;
import br.com.condominioauditoria.api.dto.request.budget.LineBudgetItemRequest;
import br.com.condominioauditoria.api.dto.request.budget.NewBudgetItemRequest;
import br.com.condominioauditoria.api.dto.request.budget.RenameBudgetItemRequest;
import br.com.condominioauditoria.api.dto.response.budget.BudgetItemBatchResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetItemEventResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetItemResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetItemSuggestionsResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetItemsResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetLineItemResponse;
import br.com.condominioauditoria.api.model.enums.BudgetItemFilter;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.budget.BudgetItemService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The condominium's budget items and the item of each line of the confirmed budgets (RF-11.7; ADR 0005, Decisions 1 and
 * 5). Reading: every role of the condominium. Creating, renaming, choosing, confirming, rejecting and suggesting: only
 * the Admin (Gestor and Usuário get 403).
 */
@RestController
@RequestMapping("/api/condominiums/{condominiumId}")
public class BudgetItemController {

    private final CondominiumAccess access;
    private final BudgetItemService service;

    public BudgetItemController(CondominiumAccess access, BudgetItemService service) {
        this.access = access;
        this.service = service;
    }

    @GetMapping("/budget-items")
    @PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')")
    public List<BudgetItemResponse> catalog(@PathVariable UUID condominiumId) {
        access.require(condominiumId);
        return service.catalog(condominiumId);
    }

    @PostMapping("/budget-items")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public BudgetItemResponse create(@PathVariable UUID condominiumId, @RequestBody NewBudgetItemRequest request) {
        access.require(condominiumId);
        return service.create(condominiumId, request, access.username());
    }

    @PutMapping("/budget-items/{itemId}")
    @PreAuthorize("hasRole('ADMIN')")
    public BudgetItemResponse rename(@PathVariable UUID condominiumId, @PathVariable UUID itemId,
            @RequestBody RenameBudgetItemRequest request) {
        access.require(condominiumId);
        return service.rename(condominiumId, itemId, request, access.username());
    }

    @GetMapping("/budgets/{budgetId}/budget-items")
    @PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')")
    public BudgetItemsResponse list(@PathVariable UUID condominiumId, @PathVariable UUID budgetId,
            @RequestParam(name = "filter", required = false) BudgetItemFilter filter) {
        access.require(condominiumId);
        return service.list(condominiumId, budgetId, filter);
    }

    @GetMapping("/budgets/{budgetId}/budget-items/events")
    @PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')")
    public List<BudgetItemEventResponse> events(@PathVariable UUID condominiumId, @PathVariable UUID budgetId) {
        access.require(condominiumId);
        return service.events(condominiumId, budgetId);
    }

    @PutMapping("/budgets/{budgetId}/budget-items/{lineId}")
    @PreAuthorize("hasRole('ADMIN')")
    public BudgetLineItemResponse setItem(@PathVariable UUID condominiumId, @PathVariable UUID budgetId,
            @PathVariable UUID lineId,
            @RequestBody LineBudgetItemRequest request) {
        access.require(condominiumId);
        return service.setItem(condominiumId, budgetId, lineId, request, access.username());
    }

    @PostMapping("/budgets/{budgetId}/budget-items/batch")
    @PreAuthorize("hasRole('ADMIN')")
    public BudgetItemBatchResponse batch(@PathVariable UUID condominiumId, @PathVariable UUID budgetId,
            @RequestBody BudgetItemBatchRequest request) {
        access.require(condominiumId);
        return service.batch(condominiumId, budgetId, request, access.username());
    }

    @PostMapping("/budgets/{budgetId}/budget-items/suggestions")
    @PreAuthorize("hasRole('ADMIN')")
    public BudgetItemSuggestionsResponse suggest(@PathVariable UUID condominiumId, @PathVariable UUID budgetId) {
        access.require(condominiumId);
        return service.suggest(condominiumId, budgetId, access.username());
    }
}
