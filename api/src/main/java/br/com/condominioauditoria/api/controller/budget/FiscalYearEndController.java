package br.com.condominioauditoria.api.controller.budget;

import br.com.condominioauditoria.api.dto.request.budget.FiscalYearEndRequest;
import br.com.condominioauditoria.api.dto.response.budget.BudgetDetailResponse;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.budget.BudgetExtensionService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Correction of the end of the fiscal year of a confirmed budget (RF-13.17; ADR 0009, Decision 6). Admin only. */
@RestController
@RequestMapping("/api/condominiums/{condominiumId}/budgets/{budgetId}/fiscal-year-end")
public class FiscalYearEndController {

    private final CondominiumAccess access;
    private final BudgetExtensionService service;

    public FiscalYearEndController(CondominiumAccess access, BudgetExtensionService service) {
        this.access = access;
        this.service = service;
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public BudgetDetailResponse correct(@PathVariable UUID condominiumId, @PathVariable UUID budgetId,
            @RequestBody FiscalYearEndRequest request) {
        access.require(condominiumId);
        return service.correctFiscalYearEnd(condominiumId, budgetId, request, access.username());
    }
}
