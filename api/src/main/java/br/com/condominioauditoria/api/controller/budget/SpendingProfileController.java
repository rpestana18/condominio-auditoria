package br.com.condominioauditoria.api.controller.budget;

import br.com.condominioauditoria.api.dto.request.budget.SpendingProfileBatchRequest;
import br.com.condominioauditoria.api.dto.response.budget.SpendingProfileEventResponse;
import br.com.condominioauditoria.api.dto.response.budget.SpendingProfilesResponse;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.budget.SpendingProfileService;
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
 * Spending profile of the budget lines (RF-13.3; ADR 0007, Decision 2). Reading: every role of the condominium.
 * Confirming: only the Admin (Gestor and Usuário get 403).
 */
@RestController
@RequestMapping("/api/condominiums/{condominiumId}/budgets/{budgetId}/spending-profiles")
public class SpendingProfileController {

    private final CondominiumAccess access;
    private final SpendingProfileService service;

    public SpendingProfileController(CondominiumAccess access, SpendingProfileService service) {
        this.access = access;
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')")
    public SpendingProfilesResponse list(@PathVariable UUID condominiumId, @PathVariable UUID budgetId) {
        access.require(condominiumId);
        return service.list(condominiumId, budgetId);
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public SpendingProfilesResponse confirm(@PathVariable UUID condominiumId, @PathVariable UUID budgetId,
            @RequestBody SpendingProfileBatchRequest request) {
        access.require(condominiumId);
        return service.confirm(condominiumId, budgetId, request, access.username());
    }

    @GetMapping("/events")
    @PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')")
    public List<SpendingProfileEventResponse> events(@PathVariable UUID condominiumId, @PathVariable UUID budgetId) {
        access.require(condominiumId);
        return service.events(condominiumId, budgetId);
    }
}
