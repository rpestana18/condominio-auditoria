package br.com.condominioauditoria.api.controller.budget;

import br.com.condominioauditoria.api.dto.request.budget.BudgetExtensionRequest;
import br.com.condominioauditoria.api.dto.response.budget.BudgetDetailResponse;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.budget.BudgetExtensionService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Extended budget (RF-11.3; ADR 0005, Decisions 4 and 5). Marking and undoing: only the Admin (Gestor and Usuário get
 * 403). The extension shows in the budget summary, which every role reads.
 */
@RestController
@RequestMapping("/api/condominiums/{condominiumId}/budgets/{budgetId}/extension")
public class BudgetExtensionController {

    private final CondominiumAccess access;
    private final BudgetExtensionService service;

    public BudgetExtensionController(CondominiumAccess access, BudgetExtensionService service) {
        this.access = access;
        this.service = service;
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public BudgetDetailResponse extend(@PathVariable UUID condominiumId, @PathVariable UUID budgetId,
            @RequestBody BudgetExtensionRequest request) {
        access.require(condominiumId);
        return service.extend(condominiumId, budgetId, request, access.username());
    }

    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    public BudgetDetailResponse undo(@PathVariable UUID condominiumId, @PathVariable UUID budgetId) {
        access.require(condominiumId);
        return service.undo(condominiumId, budgetId, access.username());
    }
}
