package br.com.condominioauditoria.api.controller.budget;

import br.com.condominioauditoria.api.dto.request.budget.BudgetLineOriginRequest;
import br.com.condominioauditoria.api.dto.request.budget.LineReviewBatchRequest;
import br.com.condominioauditoria.api.dto.response.budget.BudgetLineReviewEventResponse;
import br.com.condominioauditoria.api.dto.response.budget.BudgetLineReviewResponse;
import br.com.condominioauditoria.api.dto.response.budget.LineReviewLineResponse;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.budget.BudgetLineReviewService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Review of the budget lines and their origin (RF-13.12 and RF-13.13; ADR 0009, Decisions 1 and 3). Reading: every
 * role of the condominium. Reviewing, marking and changing the origin: only the Admin (Gestor and Usuário get 403).
 */
@RestController
@RequestMapping("/api/condominiums/{condominiumId}/budgets/{budgetId}")
public class BudgetLineReviewController {

    private final CondominiumAccess access;
    private final BudgetLineReviewService service;

    public BudgetLineReviewController(CondominiumAccess access, BudgetLineReviewService service) {
        this.access = access;
        this.service = service;
    }

    @GetMapping("/line-review")
    @PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')")
    public BudgetLineReviewResponse list(@PathVariable UUID condominiumId, @PathVariable UUID budgetId) {
        access.require(condominiumId);
        return service.list(condominiumId, budgetId);
    }

    @PutMapping("/line-review")
    @PreAuthorize("hasRole('ADMIN')")
    public BudgetLineReviewResponse review(@PathVariable UUID condominiumId, @PathVariable UUID budgetId,
            @RequestBody LineReviewBatchRequest request) {
        access.require(condominiumId);
        return service.review(condominiumId, budgetId, request, access.username());
    }

    @GetMapping("/line-review/events")
    @PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')")
    public List<BudgetLineReviewEventResponse> events(@PathVariable UUID condominiumId, @PathVariable UUID budgetId) {
        access.require(condominiumId);
        return service.events(condominiumId, budgetId);
    }

    @PutMapping("/lines/{lineId}/origin")
    @PreAuthorize("hasRole('ADMIN')")
    public LineReviewLineResponse origin(@PathVariable UUID condominiumId, @PathVariable UUID budgetId,
            @PathVariable UUID lineId, @RequestBody BudgetLineOriginRequest request) {
        access.require(condominiumId);
        return service.saveOrigin(condominiumId, budgetId, lineId, request, access.username());
    }
}
