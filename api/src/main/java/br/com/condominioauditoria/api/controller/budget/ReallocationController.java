package br.com.condominioauditoria.api.controller.budget;

import br.com.condominioauditoria.api.dto.request.budget.ReallocationRequest;
import br.com.condominioauditoria.api.dto.response.budget.ReallocationResponse;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.budget.ReallocationService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal reallocation (RF-03.1.7; ADR 0004, Decision 8): only the Gestor and the Admin reallocate and undo (the
 * Usuário gets 403); every role of the condominium reads.
 */
@RestController
@RequestMapping("/api/condominiums/{condominiumId}/reallocations")
public class ReallocationController {

    private final CondominiumAccess access;
    private final ReallocationService service;

    public ReallocationController(CondominiumAccess access, ReallocationService service) {
        this.access = access;
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')")
    public List<ReallocationResponse> list(@PathVariable UUID condominiumId, @RequestParam(name = "budget") UUID budgetId) {
        access.require(condominiumId);
        return service.list(condominiumId, budgetId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ReallocationResponse reallocate(@PathVariable UUID condominiumId, @RequestBody ReallocationRequest request) {
        access.require(condominiumId);
        return service.reallocate(condominiumId, request, access.username());
    }

    @DeleteMapping("/{reallocationId}")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ReallocationResponse undo(@PathVariable UUID condominiumId, @PathVariable UUID reallocationId) {
        access.require(condominiumId);
        return service.undo(condominiumId, reallocationId, access.username());
    }
}
